package com.pfasn.detective_pf.data;

/**
 * This class is used to store preferences on how to decode images and what to
 * save.
 * 
 * @author Justin Wetherell <phishman3579@gmail.com>
 */
public abstract class Preferences {

    private Preferences() {
    }

    

    // Which photos to save
    public static boolean SAVE_PREVIOUS = false;
    public static boolean SAVE_ORIGINAL = true;
    public static boolean SAVE_CHANGES = true;

    // Time between saving photos
    public static int PICTURE_DELAY = 1000;

}
