package org.renpy.android;

public class Constants {
    // Used by the google play store.
    public static String PLAY_BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAo6eilvz7xcWBceH5Cd1MQAFEf6nx9+ZUerV1eLfLc06Q7umueb2sF7It0ItHlOWK2xKz50Q5iXIgQUlhjT5h/blOJMEKaABfyLh+Saz1gK6CcozBRfzN1go+Y91Ncw5hnsfU3sc/zlq5ByPzN9dMJ674SABGkR/ou67aZOgLkdTGvpF89Gn5FwTuXCSaeD+Cw2pD6rJ0hCEyg7UVLU9YH2WCVIYkK6/VFixMw0NY0D+ANQGAUjGnQq9QVVxgs8ShMFxpw8PUL3DKGSfa1JR0V4uQjxwIN2HB4i2WXDvzZEKNoX4sMlJ1VDEwUuV24mXUTq3Uw+OJNlYpH49DAmjtfQIDAQAB";
    public static byte[] PLAY_SALT = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 };

    // Used by the expansion downloader.
    public static int fileVersion = 10;
    public static int fileSize = 0;

    // Used by the in-app purchasing code.
    public static String store = "none";
}