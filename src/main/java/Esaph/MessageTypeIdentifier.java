/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Esaph;


public abstract class MessageTypeIdentifier
{
	public static final String CMD_UserLeavedMoment = "CULM";
	public static final String CMD_UserSavedYourPostPrivate = "CUSYPP";
	public static final String CMD_UserUnsavedYourPostPrivate = "CUUYPP";
	public static final String CMD_UserSeenYourPostPrivate = "CUSEYPP";
	public static final String CMD_UserSavedYourPostInMoments = "USYPIM";
	public static final String CMD_UserSeenYourPostInMoments = "CUSEYMP";
    public static final String CMD_UserUnsavedYourPostInMoments = "CUUYPIM";
    public static final String CMD_NewMomentCreated = "CNMC";
    public static final String CMD_UserRemovedPostFromMoments = "CURPFM";
    public static final String CMD_UserRemovedPostFromPrivate = "CURPFP";
    public static final String CMD_FriendStatus = "CFS";
    public static final String CMD_NewMomentPost = "CNMP";
    public static final String CMD_NewPrivatePost = "CNPP";
    public static final String CMD_UserDisallowedYouToSeeHimPostInPrivate = "CDYTPIP";
    public static final String CMD_UserAllowedYouToSeeHimPostInPrivate = "CAYTPIP";
    public static final String CMD_UserDisallowedYouToSeeHimPostInMoments= "CDYDATSHPIM";
    public static final String CMD_UserAllowedYouToSeeHimPostInMoments= "CDUAYTSHPIM";
    public static final String CMD_UserSendTextMessageInPrivate = "CUSTMIP";
    public static final String CMD_UserSendTextMessageInGroup = "CUSTMIG";
    public static final String CMD_UsersAddedInGroup = "CUAIG";
    public static final String CMD_UserWasRemoved = "CURIG";
    public static final String CMD_YouWasKickedFromGroup = "CYWKFG";
    public static final String CMD_UserTyping = "CUTM";
    public static final String CMD_UserStopedTyping = "CUSTM";
    public static final String CMD_NEW_AUDIO = "CUNA";
    public static final String CMD_NEW_STICKER = "CUNS";
    public static final String CMD_NEW_EMOJIE = "CNE";
    public static final String CMD_NEW_SHARED_POST = "CNSP";
}