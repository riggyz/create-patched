package com.riggyz.create_patched.collision;

/**
 * Duck interface applied to {@link com.simibubi.create.content.contraptions.Contraption}
 * via mixin to expose the SoA collision list field.
 */
public interface CollisionListHolder {
	CollisionList create_patched$getCollisionList();
	void create_patched$setCollisionList(CollisionList list);
}
