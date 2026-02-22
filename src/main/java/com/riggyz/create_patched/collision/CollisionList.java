package com.riggyz.create_patched.collision;

import net.minecraft.world.phys.shapes.Shapes.DoubleLineConsumer;

/**
 * Structure of arrays containing dense bounding box data for collision checks.
 * Stores center + extents instead of min/max for cache-friendly OBB collision iteration.
 *
 * Backported from Create 6.0.9:
 * https://github.com/Creators-of-Create/Create/commit/8f30c2cccce4724ddae1067e4789f56dc3ee5eda
 */
public class CollisionList {

	public static final int DEFAULT_CAPACITY = 16;

	public double[] centerX = new double[DEFAULT_CAPACITY];
	public double[] centerY = new double[DEFAULT_CAPACITY];
	public double[] centerZ = new double[DEFAULT_CAPACITY];
	public double[] extentsX = new double[DEFAULT_CAPACITY];
	public double[] extentsY = new double[DEFAULT_CAPACITY];
	public double[] extentsZ = new double[DEFAULT_CAPACITY];

	public int size = 0;

	/**
	 * Helper to populate a CollisionList.
	 * Feed this into {@link net.minecraft.world.phys.shapes.VoxelShape#forAllBoxes}.
	 */
	public static class Populate implements DoubleLineConsumer {
		private final CollisionList collisionList;

		public Populate(CollisionList collisionList) {
			this.collisionList = collisionList;
		}

		@Override
		public void consume(double x1, double y1, double z1, double x2, double y2, double z2) {
			// Out of space, must reallocate.
			if (collisionList.size == collisionList.centerX.length) {
				int newCapacity = collisionList.centerX.length * 2;
				double[] newCenterX = new double[newCapacity];
				double[] newCenterY = new double[newCapacity];
				double[] newCenterZ = new double[newCapacity];
				double[] newExtentsX = new double[newCapacity];
				double[] newExtentsY = new double[newCapacity];
				double[] newExtentsZ = new double[newCapacity];

				System.arraycopy(collisionList.centerX, 0, newCenterX, 0, collisionList.size);
				System.arraycopy(collisionList.centerY, 0, newCenterY, 0, collisionList.size);
				System.arraycopy(collisionList.centerZ, 0, newCenterZ, 0, collisionList.size);
				System.arraycopy(collisionList.extentsX, 0, newExtentsX, 0, collisionList.size);
				System.arraycopy(collisionList.extentsY, 0, newExtentsY, 0, collisionList.size);
				System.arraycopy(collisionList.extentsZ, 0, newExtentsZ, 0, collisionList.size);

				collisionList.centerX = newCenterX;
				collisionList.centerY = newCenterY;
				collisionList.centerZ = newCenterZ;
				collisionList.extentsX = newExtentsX;
				collisionList.extentsY = newExtentsY;
				collisionList.extentsZ = newExtentsZ;
			}

			// Precompute center + extents from min/max.
			collisionList.centerX[collisionList.size] = 0.5 * (x2 + x1);
			collisionList.centerY[collisionList.size] = 0.5 * (y2 + y1);
			collisionList.centerZ[collisionList.size] = 0.5 * (z2 + z1);
			collisionList.extentsX[collisionList.size] = 0.5 * (x2 - x1);
			collisionList.extentsY[collisionList.size] = 0.5 * (y2 - y1);
			collisionList.extentsZ[collisionList.size] = 0.5 * (z2 - z1);

			++collisionList.size;
		}
	}
}
