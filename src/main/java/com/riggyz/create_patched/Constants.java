package com.riggyz.create_patched;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Universal constants used all throughout the mod. Defined here so that there
 * is a single point of modification for magic numbers and strings.
 */
public class Constants {

	// NOTE: Meta constants
	/** Constant for the mod id */
	public static final String MOD_ID = "create_patched";
	/** Constant for the mod name */
	public static final String MOD_NAME = "Create Patched";
	/** Constant for the mod specific logger */
	public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
}