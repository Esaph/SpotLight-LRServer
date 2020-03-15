/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

import java.sql.SQLException;
import java.util.UUID;
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;

import Esaph.LogUtilsEsaph;

public class SessionHandler
{
	private LogUtilsEsaph logUtilsMain;
	private Connection connection;
	private static final String QUERY_REGISTER_NEW_SESSION = "INSERT INTO Sessions (UID, SID) values (?, ?)";
	private static final String QUERY_DELETE_SESSION = "DELETE FROM Sessions WHERE UID=?";
	
	public SessionHandler(Connection connection, LogUtilsEsaph logUtilsMain)
	{
		this.connection = connection;
		this.logUtilsMain = logUtilsMain;
	}
	
	
	public boolean deleteSession(long UID)
	{
		try
		{
			PreparedStatement prRMState = (PreparedStatement) this.connection.prepareStatement(SessionHandler.QUERY_DELETE_SESSION);
			prRMState.setLong(1, UID);
			prRMState.executeUpdate();
			prRMState.close();
			logUtilsMain.writeLog("Session deleted.");
			return true;
		}
		catch (Exception e1)
		{
			logUtilsMain.writeLog("SessionHandler_FatalERROR: " + e1);
			return false;
		}
	}
	
	public String createNewSession(long UID)
	{
		try
		{
			if(this.deleteSession(UID))
			{
				String SID = UUID.randomUUID().toString();
				PreparedStatement prstate = (PreparedStatement) this.connection.prepareStatement(SessionHandler.QUERY_REGISTER_NEW_SESSION);
				prstate.setLong(1, UID);
				prstate.setString(2, SID);
				prstate.executeUpdate();
				prstate.close();
				
				logUtilsMain.writeLog("Session succesfully created: " + SID);
				return SID;
			}
			logUtilsMain.writeLog("Session failed to create.");
			return "ERROR";
		}
		catch (SQLException e)
		{
			logUtilsMain.writeLog("Failed creating Session: " + e);
			try
			{
				deleteSession(UID);
			}
			catch(Exception ec)
			{
				logUtilsMain.writeLog("(ERROR)Connection to SQL couldnt be closed!");
				deleteSession(UID);
			}
			return "ERROR";
		}
		catch(Exception e)
		{
			logUtilsMain.writeLog("Session FATAL ERROR: " + e);
			try
			{
				deleteSession(UID);
			}
			catch(Exception ec)
			{
				logUtilsMain.writeLog("(FATAL ERROR)Connection to SQL couldnt be closed!");
				deleteSession(UID);
			}
			return "ERROR";
		}
	}
}
