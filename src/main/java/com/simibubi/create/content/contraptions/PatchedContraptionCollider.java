package com.simibubi.create.content.contraptions;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.MutablePair;

import com.riggyz.create_patched.collision.CollisionList;
import com.riggyz.create_patched.collision.CollisionListHolder;
import com.riggyz.create_patched.collision.OrientedBBExtension;
import com.riggyz.create_patched.mixin.ContraptionColliderAccessor;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity.ContraptionRotationState;
import com.simibubi.create.content.contraptions.ContraptionCollider.PlayerType;
import com.simibubi.create.content.contraptions.sync.ClientMotionPacket;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.collision.ContinuousOBBCollider.ContinuousSeparationManifold;
import com.simibubi.create.foundation.collision.Matrix3d;
import com.simibubi.create.foundation.collision.OrientedBB;
import com.simibubi.create.foundation.damageTypes.CreateDamageSources;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;

import java.util.Collections;

/**
 * Replacement implementation of ContraptionCollider.collideEntities() that uses
 * SoA CollisionList instead of List&lt;AABB&gt; for oriented collision checks.
 *
 * This class lives in Create's package so it has natural access to package-private
 * members like PlayerType, bounceEntity, collide, etc.
 *
 * @see <a href="https://github.com/Creators-of-Create/Create/commit/8f30c2cccce4724ddae1067e4789f56dc3ee5eda">
 *     Orient data for oriented collision</a>
 */
public class PatchedContraptionCollider {

	public static void collideEntities(AbstractContraptionEntity contraptionEntity) {
		Level world = contraptionEntity.getCommandSenderWorld();
		Contraption contraption = contraptionEntity.getContraption();
		AABB bounds = contraptionEntity.getBoundingBox();

		if (contraption == null)
			return;
		if (bounds == null)
			return;

		Vec3 contraptionPosition = contraptionEntity.position();
		Vec3 contraptionMotion = contraptionPosition.subtract(contraptionEntity.getPrevPositionVec());
		Vec3 anchorVec = contraptionEntity.getAnchorVec();
		ContraptionRotationState rotation = null;

		MutablePair<WeakReference<AbstractContraptionEntity>, Double> safetyLock =
			ContraptionColliderAccessor.create_patched$getSafetyLock();

		if (world.isClientSide() && safetyLock.left != null && safetyLock.left.get() == contraptionEntity)
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> saveClientPlayerFromClipping(contraptionEntity, contraptionMotion));

		// After death, multiple refs to the client player may show up in the area
		boolean skipClientPlayer = false;

		List<Entity> entitiesWithinAABB = world.getEntitiesOfClass(Entity.class, bounds.inflate(2)
			.expandTowards(0, 32, 0), contraptionEntity::canCollideWith);
		for (Entity entity : entitiesWithinAABB) {
			if (!entity.isAlive())
				continue;

			PlayerType playerType = getPlayerType(entity);
			if (playerType == PlayerType.REMOTE) {
				if (!(contraption instanceof TranslatingContraption))
					continue;
				DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
					() -> () -> saveRemotePlayerFromClipping((Player) entity, contraptionEntity, contraptionMotion));
				continue;
			}

			entity.getSelfAndPassengers()
				.forEach(e -> {
					if (e instanceof ServerPlayer)
						((ServerPlayer) e).connection.aboveGroundTickCount = 0;
				});

			if (playerType == PlayerType.SERVER)
				continue;

			if (playerType == PlayerType.CLIENT) {
				if (skipClientPlayer)
					continue;
				else
					skipClientPlayer = true;
			}

			// Init matrix
			if (rotation == null)
				rotation = contraptionEntity.getRotationState();
			Matrix3d rotationMatrix = rotation.asMatrix();

			// Transform entity position and motion to local space
			Vec3 entityPosition = entity.position();
			AABB entityBounds = entity.getBoundingBox();
			Vec3 motion = entity.getDeltaMovement();
			float yawOffset = rotation.getYawOffset();
			Vec3 position = ContraptionCollider.getWorldToLocalTranslation(entity, anchorVec, rotationMatrix, yawOffset);

			// Make player 'shorter' to make it less likely to become stuck
			if (playerType == PlayerType.CLIENT && entityBounds.getYsize() > 1)
				entityBounds = entityBounds.contract(0, 2 / 16f, 0);

			motion = motion.subtract(contraptionMotion);
			motion = rotationMatrix.transform(motion);

			// Prepare entity bounds
			AABB localBB = entityBounds.move(position)
				.inflate(1.0E-7D);

			OrientedBB obb = new OrientedBB(localBB);
			obb.setRotation(rotationMatrix);

			// Use simplified bbs when present - now using SoA CollisionList
			final Vec3 motionCopy = motion;
			CollisionList collidableBBs;

			// Check if pre-computed colliders exist via the original Optional signal
			if (contraption.getSimplifiedEntityColliders().isPresent()) {
				collidableBBs = ((CollisionListHolder) contraption).create_patched$getCollisionList();
				if (collidableBBs == null) {
					// Fallback: signal says ready but CollisionList not yet populated
					collidableBBs = computeCollisionListOnTheFly(world, contraption, localBB.expandTowards(motionCopy));
				}
			} else {
				// No pre-computed colliders - compute on the fly from nearby block shapes
				collidableBBs = computeCollisionListOnTheFly(world, contraption, localBB.expandTowards(motionCopy));
			}

			MutableObject<Vec3> collisionResponse = new MutableObject<>(Vec3.ZERO);
			MutableObject<Vec3> normal = new MutableObject<>(Vec3.ZERO);
			MutableObject<Vec3> location = new MutableObject<>(Vec3.ZERO);
			MutableBoolean surfaceCollision = new MutableBoolean(false);
			MutableFloat temporalResponse = new MutableFloat(1);
			Vec3 obbCenter = obb.getCenter();

			// Apply separation maths
			boolean doHorizontalPass = !rotation.hasVerticalRotation();
			for (boolean horizontalPass : Iterate.trueAndFalse) {
				boolean verticalPass = !horizontalPass || !doHorizontalPass;

				// SoA indexed loop instead of for-each over AABB list
				for (int bbIdx = 0; bbIdx < collidableBBs.size; ++bbIdx) {
					Vec3 currentResponse = collisionResponse.getValue();
					Vec3 currentCenter = obbCenter.add(currentResponse);

					// Early-out using SoA center/extents instead of AABB getCenter/getSize
					if (Math.abs(currentCenter.x - collidableBBs.centerX[bbIdx]) - entityBounds.getXsize() - 1 > collidableBBs.extentsX[bbIdx])
						continue;
					if (Math.abs((currentCenter.y + motion.y) - collidableBBs.centerY[bbIdx]) - entityBounds.getYsize()
						- 1 > collidableBBs.extentsY[bbIdx])
						continue;
					if (Math.abs(currentCenter.z - collidableBBs.centerZ[bbIdx]) - entityBounds.getZsize() - 1 > collidableBBs.extentsZ[bbIdx])
						continue;

					obb.setCenter(currentCenter);
					// Use new intersect overload that reads directly from CollisionList
					ContinuousSeparationManifold intersect = ((OrientedBBExtension) obb).create_patched$intersect(collidableBBs, bbIdx, motion);

					if (intersect == null)
						continue;
					if (verticalPass && surfaceCollision.isFalse())
						surfaceCollision.setValue(intersect.isSurfaceCollision());

					double timeOfImpact = intersect.getTimeOfImpact();
					boolean isTemporal = timeOfImpact > 0 && timeOfImpact < 1;
					Vec3 collidingNormal = intersect.getCollisionNormal();
					Vec3 collisionPosition = intersect.getCollisionPosition();

					if (!isTemporal) {
						Vec3 separation = intersect.asSeparationVec(entity.getStepHeight());
						if (separation != null && !separation.equals(Vec3.ZERO)) {
							collisionResponse.setValue(currentResponse.add(separation));
							timeOfImpact = 0;
						}
					}

					boolean nearest = timeOfImpact >= 0 && temporalResponse.getValue() > timeOfImpact;
					if (collidingNormal != null && nearest)
						normal.setValue(collidingNormal);
					if (collisionPosition != null && nearest)
						location.setValue(collisionPosition);

					if (isTemporal) {
						if (temporalResponse.getValue() > timeOfImpact)
							temporalResponse.setValue(timeOfImpact);
					}
				}

				if (verticalPass)
					break;

				boolean noVerticalMotionResponse = temporalResponse.getValue() == 1;
				boolean noVerticalCollision = collisionResponse.getValue().y == 0;
				if (noVerticalCollision && noVerticalMotionResponse)
					break;

				// Re-run collisions with horizontal offset
				collisionResponse.setValue(collisionResponse.getValue()
					.multiply(129 / 128f, 0, 129 / 128f));
				// Note: the original had a stray 'continue' here which has been removed
			}

			// Resolve collision
			Vec3 entityMotion = entity.getDeltaMovement();
			Vec3 entityMotionNoTemporal = entityMotion;
			Vec3 collisionNormal = normal.getValue();
			Vec3 collisionLocation = location.getValue();
			Vec3 totalResponse = collisionResponse.getValue();
			boolean hardCollision = !totalResponse.equals(Vec3.ZERO);
			boolean temporalCollision = temporalResponse.getValue() != 1;
			Vec3 motionResponse = !temporalCollision ? motion
				: motion.normalize()
					.scale(motion.length() * temporalResponse.getValue());

			rotationMatrix.transpose();
			motionResponse = rotationMatrix.transform(motionResponse)
				.add(contraptionMotion);
			totalResponse = rotationMatrix.transform(totalResponse);
			totalResponse = VecHelper.rotate(totalResponse, yawOffset, Axis.Y);
			collisionNormal = rotationMatrix.transform(collisionNormal);
			collisionNormal = VecHelper.rotate(collisionNormal, yawOffset, Axis.Y);
			collisionNormal = collisionNormal.normalize();
			collisionLocation = rotationMatrix.transform(collisionLocation);
			collisionLocation = VecHelper.rotate(collisionLocation, yawOffset, Axis.Y);
			rotationMatrix.transpose();

			double bounce = 0;
			double slide = 0;

			if (!collisionLocation.equals(Vec3.ZERO)) {
				collisionLocation = collisionLocation.add(entity.position()
					.add(entity.getBoundingBox()
						.getCenter())
					.scale(.5f));
				if (temporalCollision)
					collisionLocation = collisionLocation.add(0, motionResponse.y, 0);

				BlockPos pos = BlockPos.containing(contraptionEntity.toLocalVector(entity.position(), 0));
				if (contraption.getBlocks()
					.containsKey(pos)) {
					BlockState blockState = contraption.getBlocks()
						.get(pos).state();
					if (blockState.is(BlockTags.CLIMBABLE)) {
						surfaceCollision.setTrue();
						totalResponse = totalResponse.add(0, .1f, 0);
					}
				}

				pos = BlockPos.containing(contraptionEntity.toLocalVector(collisionLocation, 0));
				if (contraption.getBlocks()
					.containsKey(pos)) {
					BlockState blockState = contraption.getBlocks()
						.get(pos).state();

					com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour movingInteractionBehaviour =
						contraption.getInteractors().get(pos);
					if (movingInteractionBehaviour != null)
						movingInteractionBehaviour.handleEntityCollision(entity, pos, contraptionEntity);

					bounce = BlockHelper.getBounceMultiplier(blockState.getBlock());
					slide = Math.max(0, blockState.getFriction(contraption.getContraptionWorld(), pos, entity) - .6f);
				}
			}

			boolean hasNormal = !collisionNormal.equals(Vec3.ZERO);
			boolean anyCollision = hardCollision || temporalCollision;

			if (bounce > 0 && hasNormal && anyCollision
				&& ContraptionCollider.bounceEntity(entity, collisionNormal, contraptionEntity, bounce)) {
				entity.level().playSound(playerType == PlayerType.CLIENT ? (Player) entity : null, entity.getX(),
					entity.getY(), entity.getZ(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.BLOCKS, .5f, 1);
				continue;
			}

			if (temporalCollision) {
				double idealVerticalMotion = motionResponse.y;
				if (idealVerticalMotion != entityMotion.y) {
					entity.setDeltaMovement(entityMotion.multiply(1, 0, 1)
						.add(0, idealVerticalMotion, 0));
					entityMotion = entity.getDeltaMovement();
				}
			}

			if (hardCollision) {
				double motionX = entityMotion.x();
				double motionY = entityMotion.y();
				double motionZ = entityMotion.z();
				double intersectX = totalResponse.x();
				double intersectY = totalResponse.y();
				double intersectZ = totalResponse.z();

				double horizonalEpsilon = 1 / 128f;
				if (motionX != 0 && Math.abs(intersectX) > horizonalEpsilon && motionX > 0 == intersectX < 0)
					entityMotion = entityMotion.multiply(0, 1, 1);
				if (motionY != 0 && intersectY != 0 && motionY > 0 == intersectY < 0)
					entityMotion = entityMotion.multiply(1, 0, 1)
						.add(0, contraptionMotion.y, 0);
				if (motionZ != 0 && Math.abs(intersectZ) > horizonalEpsilon && motionZ > 0 == intersectZ < 0)
					entityMotion = entityMotion.multiply(1, 1, 0);

			}

			if (bounce == 0 && slide > 0 && hasNormal && anyCollision && rotation.hasVerticalRotation()) {
				double slideFactor = collisionNormal.multiply(1, 0, 1)
					.length() * 1.25f;
				Vec3 motionIn = entityMotionNoTemporal.multiply(0, .9, 0)
					.add(0, -.01f, 0);
				Vec3 slideNormal = collisionNormal.cross(motionIn.cross(collisionNormal))
					.normalize();
				Vec3 newMotion = entityMotion.multiply(.85, 0, .85)
					.add(slideNormal.scale((.2f + slide) * motionIn.length() * slideFactor)
						.add(0, -.1f - collisionNormal.y * .125f, 0));
				entity.setDeltaMovement(newMotion);
				entityMotion = entity.getDeltaMovement();
			}

			if (!hardCollision && surfaceCollision.isFalse())
				continue;

			Vec3 allowedMovement = ContraptionCollider.collide(totalResponse, entity);
			entity.setPos(entityPosition.x + allowedMovement.x, entityPosition.y + allowedMovement.y,
				entityPosition.z + allowedMovement.z);
			entityPosition = entity.position();

			entityMotion =
				handleDamageFromTrain(world, contraptionEntity, contraptionMotion, entity, entityMotion, playerType);

			entity.hurtMarked = true;
			Vec3 contactPointMotion = Vec3.ZERO;

			if (surfaceCollision.isTrue()) {
				contraptionEntity.registerColliding(entity);
				entity.fallDistance = 0;
				for (Entity rider : entity.getIndirectPassengers())
					if (getPlayerType(rider) == PlayerType.CLIENT)
						AllPackets.getChannel()
							.sendToServer(new ClientMotionPacket(rider.getDeltaMovement(), true, 0));
				boolean canWalk = bounce != 0 || slide == 0;
				if (canWalk || !rotation.hasVerticalRotation()) {
					if (canWalk)
						entity.setOnGround(true);
					if (entity instanceof ItemEntity)
						entityMotion = entityMotion.multiply(.5f, 1, .5f);
				}
				contactPointMotion = contraptionEntity.getContactPointMotion(entityPosition);
				allowedMovement = ContraptionCollider.collide(contactPointMotion, entity);
				entity.setPos(entityPosition.x + allowedMovement.x, entityPosition.y,
					entityPosition.z + allowedMovement.z);
			}

			entity.setDeltaMovement(entityMotion);

			if (playerType != PlayerType.CLIENT)
				continue;

			double d0 = entity.getX() - entity.xo - contactPointMotion.x;
			double d1 = entity.getZ() - entity.zo - contactPointMotion.z;
			float limbSwing = Mth.sqrt((float) (d0 * d0 + d1 * d1)) * 4.0F;
			if (limbSwing > 1.0F)
				limbSwing = 1.0F;
			AllPackets.getChannel()
				.sendToServer(new ClientMotionPacket(entityMotion, true, limbSwing));

			if (entity.onGround() && contraption instanceof TranslatingContraption) {
				safetyLock.setLeft(new WeakReference<>(contraptionEntity));
				safetyLock.setRight(entity.getY() - contraptionEntity.getY());
			}
		}

	}

	// ---- Inlined private helpers from ContraptionCollider ----
	// These are duplicated here because they are private in the original class
	// and we need them for the replacement implementation.

	/**
	 * Computes a CollisionList on-the-fly from nearby block shapes.
	 * Replaces the original getPotentiallyCollidedShapes + toAabbs pattern.
	 */
	private static CollisionList computeCollisionListOnTheFly(Level world, Contraption contraption, AABB localBB) {
		double height = localBB.getYsize();
		double width = localBB.getXsize();
		double horizontalFactor = (height > width && width != 0) ? height / width : 1;
		double verticalFactor = (width > height && height != 0) ? width / height : 1;
		AABB blockScanBB = localBB.inflate(0.5f);
		blockScanBB = blockScanBB.inflate(horizontalFactor, verticalFactor, horizontalFactor);

		BlockPos min = BlockPos.containing(blockScanBB.minX, blockScanBB.minY, blockScanBB.minZ);
		BlockPos max = BlockPos.containing(blockScanBB.maxX, blockScanBB.maxY, blockScanBB.maxZ);

		CollisionList out = new CollisionList();
		CollisionList.Populate populate = new CollisionList.Populate(out);

		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
			if (contraption.getBlocks().containsKey(p) && !contraption.isHiddenInPortal(p)) {
				StructureBlockInfo info = contraption.getBlocks().get(p);
				BlockState blockState = info.state();
				BlockPos pos = info.pos();

				VoxelShape collisionShape = blockState.getCollisionShape(world, p)
					.move(pos.getX(), pos.getY(), pos.getZ());

				if (!collisionShape.isEmpty()) {
					collisionShape.forAllBoxes(populate);
				}
			}
		}

		return out;
	}

	// Duplicated from ContraptionCollider - private method
	private static PlayerType getPlayerType(Entity entity) {
		if (!(entity instanceof Player))
			return PlayerType.NONE;
		if (!entity.level().isClientSide)
			return PlayerType.SERVER;
		MutableBoolean isClient = new MutableBoolean(false);
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> isClient.setValue(isClientPlayerEntity(entity)));
		return isClient.booleanValue() ? PlayerType.CLIENT : PlayerType.REMOTE;
	}

	@OnlyIn(Dist.CLIENT)
	private static boolean isClientPlayerEntity(Entity entity) {
		return entity instanceof LocalPlayer;
	}

	// Duplicated from ContraptionCollider - private @OnlyIn(Dist.CLIENT) method
	@OnlyIn(Dist.CLIENT)
	private static void saveClientPlayerFromClipping(AbstractContraptionEntity contraptionEntity,
		Vec3 contraptionMotion) {
		LocalPlayer entity = Minecraft.getInstance().player;
		if (entity.isPassenger())
			return;

		MutablePair<WeakReference<AbstractContraptionEntity>, Double> safetyLock =
			ContraptionColliderAccessor.create_patched$getSafetyLock();

		double prevDiff = safetyLock.right;
		double currentDiff = entity.getY() - contraptionEntity.getY();
		double motion = contraptionMotion.subtract(entity.getDeltaMovement()).y;
		double trend = Math.signum(currentDiff - prevDiff);

		ClientPacketListener handler = entity.connection;
		if (handler.getOnlinePlayers()
			.size() > 1) {
			// packetCooldown is private - we duplicate its behavior with a local static field
			if (packetCooldown > 0)
				packetCooldown--;
			if (packetCooldown == 0) {
				AllPackets.getChannel()
					.sendToServer(new ContraptionColliderLockPacket.ContraptionColliderLockPacketRequest(
						contraptionEntity.getId(), currentDiff));
				packetCooldown = 3;
			}
		}

		if (trend == 0)
			return;
		if (trend == Math.signum(motion))
			return;

		double speed = contraptionMotion.multiply(0, 1, 0)
			.lengthSqr();
		if (trend > 0 && speed < 0.1)
			return;
		if (speed < 0.05)
			return;

		if (!savePlayerFromClipping(entity, contraptionEntity, contraptionMotion, prevDiff))
			safetyLock.setLeft(null);
	}

	// Duplicated from ContraptionCollider - private @OnlyIn(Dist.CLIENT) method
	@OnlyIn(Dist.CLIENT)
	private static void saveRemotePlayerFromClipping(Player entity, AbstractContraptionEntity contraptionEntity,
		Vec3 contraptionMotion) {
		if (entity.isPassenger())
			return;

		Map<AbstractContraptionEntity, Map<Player, Double>> remoteSafetyLocks =
			ContraptionColliderAccessor.create_patched$getRemoteSafetyLocks();

		Map<Player, Double> locksOnThisContraption =
			remoteSafetyLocks.getOrDefault(contraptionEntity, Collections.emptyMap());
		double prevDiff = locksOnThisContraption.getOrDefault(entity, entity.getY() - contraptionEntity.getY());
		if (!savePlayerFromClipping(entity, contraptionEntity, contraptionMotion, prevDiff))
			if (locksOnThisContraption.containsKey(entity))
				locksOnThisContraption.remove(entity);
	}

	// Duplicated from ContraptionCollider - private @OnlyIn(Dist.CLIENT) method
	@OnlyIn(Dist.CLIENT)
	private static boolean savePlayerFromClipping(Player entity, AbstractContraptionEntity contraptionEntity,
		Vec3 contraptionMotion, double yStartOffset) {
		AABB bb = entity.getBoundingBox()
			.deflate(1 / 4f, 0, 1 / 4f);
		double shortestDistance = Double.MAX_VALUE;
		double yStart = entity.getStepHeight() + contraptionEntity.getY() + yStartOffset;
		double rayLength = Math.max(5, Math.abs(entity.getY() - yStart));

		for (int rayIndex = 0; rayIndex < 4; rayIndex++) {
			Vec3 start = new Vec3(rayIndex / 2 == 0 ? bb.minX : bb.maxX, yStart, rayIndex % 2 == 0 ? bb.minZ : bb.maxZ);
			Vec3 end = start.add(0, -rayLength, 0);

			BlockHitResult hitResult = ContraptionHandlerClient.rayTraceContraption(start, end, contraptionEntity);
			if (hitResult == null)
				continue;

			Vec3 hit = contraptionEntity.toGlobalVector(hitResult.getLocation(), 1);
			double hitDiff = start.y - hit.y;
			if (shortestDistance > hitDiff)
				shortestDistance = hitDiff;
		}

		if (shortestDistance > rayLength)
			return false;
		entity.setPos(entity.getX(), yStart - shortestDistance, entity.getZ());
		return true;
	}

	// Duplicated from ContraptionCollider - private method
	private static Vec3 handleDamageFromTrain(Level world, AbstractContraptionEntity contraptionEntity,
		Vec3 contraptionMotion, Entity entity, Vec3 entityMotion, PlayerType playerType) {

		if (!(contraptionEntity instanceof CarriageContraptionEntity cce))
			return entityMotion;
		if (!entity.onGround())
			return entityMotion;

		CompoundTag persistentData = entity.getPersistentData();
		if (persistentData.contains("ContraptionGrounded")) {
			persistentData.remove("ContraptionGrounded");
			return entityMotion;
		}

		if (cce.collidingEntities.containsKey(entity))
			return entityMotion;
		if (entity instanceof ItemEntity)
			return entityMotion;
		if (cce.nonDamageTicks != 0)
			return entityMotion;
		if (!AllConfigs.server().trains.trainsCauseDamage.get())
			return entityMotion;

		Vec3 diffMotion = contraptionMotion.subtract(entity.getDeltaMovement());

		if (diffMotion.length() <= 0.35f || contraptionMotion.length() <= 0.35f)
			return entityMotion;

		DamageSource source = CreateDamageSources.runOver(world, contraptionEntity);
		double damage = diffMotion.length();
		if (entity.getClassification(false) == MobCategory.MONSTER)
			damage *= 2;

		if (entity instanceof Player p && (p.isCreative() || p.isSpectator()))
			return entityMotion;

		if (playerType == PlayerType.CLIENT) {
			AllPackets.getChannel()
				.sendToServer(new TrainCollisionPacket((int) (damage * 16), contraptionEntity.getId()));
			world.playSound((Player) entity, entity.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
				SoundSource.NEUTRAL, 1, .75f);
		} else {
			entity.hurt(source, (int) (damage * 16));
			world.playSound(null, entity.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 1, .75f);
			if (!entity.isAlive())
				contraptionEntity.getControllingPlayer()
					.map(world::getPlayerByUUID)
					.ifPresent(AllAdvancements.TRAIN_ROADKILL::awardTo);
		}

		Vec3 added = entityMotion.add(contraptionMotion.multiply(1, 0, 1)
			.normalize()
			.add(0, .25, 0)
			.scale(damage * 4))
			.add(diffMotion);

		return VecHelper.clamp(added, 3);
	}

	// Duplicated private static field from ContraptionCollider
	// This is a separate counter from the original, but since we completely replace
	// collideEntities, the original is never used.
	private static int packetCooldown = 0;
}
