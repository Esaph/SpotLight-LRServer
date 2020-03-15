/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

import java.math.BigInteger;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;

import Esaph.LogUtilsEsaph;

public class MailServer
{
	private LogUtilsEsaph logUtilsMain;
	private static final String username = "r276266_0-lifecapturenoreply"; //"s276266_0-esaph";
    private static final String password = "8b+E[öde249c5qbeU]g0RÖe<f3ß";
	private static final String email = "LifeCapture.noreply@esaph.de";
	private static final String smtpServer = "smtp.1blu.de";
	private String empf;
	private String tenchatUser;
	private Connection connection;
	private static int sendMailPerHour = 0;
	
	public MailServer(Connection connection, String empf, String benutzername, LogUtilsEsaph logUtilsMain)
	{
		this.connection = connection;
		this.empf = empf;
		this.tenchatUser = benutzername;
		this.logUtilsMain = logUtilsMain;
	}
	
	private final static Object lock = new Object();
	
	public static int getEmailLimit()
	{
		return 0;
		/*
		synchronized(lock)
		{
			return MailServer.sendMailPerHour;
		}*/
	}
	
	private static final String QUERY_CREATE_EMAIL_LINK = "INSERT INTO EmailVerification (Username, VID, VerificationDate) values(?, ?, ?)";
	private static final String queryDeleteRequestRegistration = "DELETE FROM EmailVerification WHERE Username=? AND VID=?";
	private static final String queryInsertNewPwRequest = "INSERT INTO PasswordResetRequest (Username, VID) values (?, ?)";
	private static final String queryDeleteRequestPwForgotten = "DELETE FROM PasswordResetRequest WHERE Username=? AND VID=?";
	
	public boolean generateVIDAndSendEmailRegistration()
	{
		return true;
		/*
		String VID = null;
		try
		{
			SecureRandom random = new SecureRandom();
			VID = new BigInteger(130, random).toString(32) + this.tenchatUser;
			PreparedStatement prstate = (PreparedStatement) this.connection.prepareStatement(MailServer.QUERY_CREATE_EMAIL_LINK);
			prstate.setString(1, this.tenchatUser);
			prstate.setString(2, VID);
			prstate.setDate(3, null);
			prstate.executeUpdate();
			this.logUtilsMain.writeLog("Verification ID succesfully created.");
			this.logUtilsMain.writeLog("Verification ID: " + VID);
			
			if(sendMail(VID))
			{
				return true;
			}
			else
			{
				PreparedStatement prDeleteRequest = (PreparedStatement) this.connection.prepareStatement(MailServer.queryDeleteRequestRegistration);
				prDeleteRequest.setString(1, this.tenchatUser);
				prDeleteRequest.setString(2, VID);
				prDeleteRequest.executeUpdate();
				prDeleteRequest.close();
				return false;
			}
		}
		catch (SQLException e)
		{
			this.logUtilsMain.writeLog("Failed creating Verification ID generateVIDAndSendEmailRegistration(): " + e);
			
			try
			{
				if(VID != null)
				{
					PreparedStatement prDeleteRequest = (PreparedStatement) this.connection.prepareStatement(MailServer.queryDeleteRequestRegistration);
					prDeleteRequest.setString(1, this.tenchatUser);
					prDeleteRequest.setString(2, VID);
					prDeleteRequest.executeUpdate();
					prDeleteRequest.close();
				}
			}
			catch(Exception ec)
			{
				this.logUtilsMain.writeLog("Failed creating Verification ID again generateVIDAndSendEmailRegistration(): " + ec);
			}
			
			return false;
		}*/
	}
	
	public boolean SendEmailRegistration()
	{
		String VID = null;
		try
		{
			SecureRandom random = new SecureRandom();
			VID = new BigInteger(130, random).toString(32) + this.tenchatUser;
			PreparedStatement prInsertNewRequest = (PreparedStatement) this.connection.prepareStatement(MailServer.queryInsertNewPwRequest);
			prInsertNewRequest.setString(1, this.tenchatUser);
			prInsertNewRequest.setString(2, VID);
			prInsertNewRequest.executeUpdate();
			prInsertNewRequest.close();
			
			if(sendMailPasswordReset(VID))
			{
				return true;
			}
			else
			{
				PreparedStatement prDeleteRequest = (PreparedStatement) this.connection.prepareStatement(MailServer.queryDeleteRequestPwForgotten);
				prDeleteRequest.setString(1, this.tenchatUser);
				prDeleteRequest.setString(2, VID);
				prDeleteRequest.executeUpdate();
				prDeleteRequest.close();
				return false;
			}
		}
		catch (Exception e)
		{
			this.logUtilsMain.writeLog("Failed creating Verification ID SendEmailRegistration(): " + e);
			try
			{
				if(VID != null)
				{
					PreparedStatement prDeleteRequest = (PreparedStatement) this.connection.prepareStatement(MailServer.queryDeleteRequestPwForgotten);
					prDeleteRequest.setString(1, this.tenchatUser);
					prDeleteRequest.setString(2, VID);
					prDeleteRequest.executeUpdate();
					prDeleteRequest.close();
				}
			}
			catch(Exception ec)
			{
				this.logUtilsMain.writeLog("Failed creating Verification ID again SendEmailRegistration(): " + ec);
			}
			return false;
		}
	}
	
	
	/*
	 *  message.setContent("<HTML>Hello, " + tenchatUser + " its TenChat time!\nPlease verify your Email from this Link:" + 
            "<A HREF=" + VID + ">Verify</A>", "text/html");
	 * 
	*/
	
	private static final String esaphVerficationEmailLink = "https://www.esaph.de/handler.php?cmd=VerifyUser";
	private boolean sendMail(String VID)
	{
        try
        {
        	String link = MailServer.esaphVerficationEmailLink + "&vid=" + VID + "&usr=" + this.tenchatUser;
        	Properties props = new Properties();
        	props.put("mail.smtp.auth", "true");
        	props.put("mail.smtp.starttls.enable", "true");
        	props.put("mail.smtp.host", MailServer.smtpServer);
        	props.put("mail.smtp.port", "587");
        

        	Session session = Session.getInstance(props,
        			new Authenticator(){
        		protected PasswordAuthentication getPasswordAuthentication()
        		{
        			return new PasswordAuthentication(username, password);
        		}	
        	});
        	
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(MailServer.email));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(this.empf));
            message.setSubject("LifeCapture verification");
            message.setContent("<HTML>Hello, " + this.tenchatUser + " its LifeCapture time!\nPlease verify your Email from this Link:" + 
                    "<A HREF=" + link + ">Verify</A></HTML>", "text/html");
            Transport.send(message);

            this.logUtilsMain.writeLog("Email sent!");
            synchronized(lock)
            {
            	MailServer.sendMailPerHour++;
            }
            return true;
        }
        catch (MessagingException e)
        {
        	this.logUtilsMain.writeLog("Exception(EMAIL): " + e);
        	return false;
        }
	}
	
	private static final String esaphVerficationEmailLinkPasswordRequest = "https://www.esaph.de/changepass.html?";
	private boolean sendMailPasswordReset(String VID)
	{
        try
        {
        	String link = MailServer.esaphVerficationEmailLinkPasswordRequest + "vid=" + VID + "&usr=" + this.tenchatUser;
        	Properties props = new Properties();
        	props.put("mail.smtp.auth", "true");
        	props.put("mail.smtp.starttls.enable", "true");
        	props.put("mail.smtp.host", MailServer.smtpServer);
        	props.put("mail.smtp.port", "587");
        

        	Session session = Session.getInstance(props,
        			new Authenticator(){
        		protected PasswordAuthentication getPasswordAuthentication()
        		{
        			return new PasswordAuthentication(username, password);
        		}	
        	});
        	
        	
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(MailServer.email));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(this.empf));
            message.setSubject("LifeCapture Password Request");
            message.setContent("<HTML>Hello, " + this.tenchatUser + "!\nPlease reset your Password from this Link:" + 
                    "<A HREF=" + link + ">Password Reset</A></HTML>", "text/html");
            Transport.send(message);

            this.logUtilsMain.writeLog("Email sent!");
            synchronized(lock)
            {
            	MailServer.sendMailPerHour++;
            }
            return true;

        }
        catch (MessagingException e)
        {
        	this.logUtilsMain.writeLog("Exception(EMAIL): " + e);
        	return false;
        }
	}
	
}
