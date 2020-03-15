/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import Esaph.*;
import org.joda.time.DateTime;
import org.joda.time.Years;
import org.json.JSONObject;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;

public class LoginRegisterServer extends Thread
{
	private LogUtilsEsaph logUtilsMain;
	private static final String mainServerLogPath = "/usr/server/Log/LRServer/";
	private static final String ServerType = "LoginRegisterServer";
	private static final String placeholder = "LoginRegisterServer: ";
	private static final String GOOGLE_API_ID = "326988803182-5lcfv450geg6lcnsjpo6j00rq9ige153.apps.googleusercontent.com";
	private SSLServerSocket serverSocket;
	private static final int port = 1028;
	private final HashMap<String, Integer> connectionMap = new HashMap<String, Integer>();
	private SQLPool pool;

	public SQLPool getLRServerPool()
	{
		return this.pool;
	}

	public LoginRegisterServer() throws IOException
	{
		logUtilsMain = new LogUtilsEsaph(new File(LoginRegisterServer.mainServerLogPath), LoginRegisterServer.ServerType, "127.0.0.1", -1);
		Timer timer = new Timer();
		timer.schedule(new unfreezeConnections(), 0, 60000);
		try
		{
			pool = new SQLPool();
			this.logUtilsMain.writeLog(LoginRegisterServer.placeholder + "Thread pool loaded().");
		}
		catch(Exception ec)
		{
			this.logUtilsMain.writeLog(LoginRegisterServer.placeholder + "Thread pool failed to load: " + ec);
		}
	}
	
	
	public void startLRServer()
	{
		try
		{
			this.initSSLKey();
		    SSLServerSocketFactory sslServerSocketFactory = this.sslContext.getServerSocketFactory();
		    this.serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(LoginRegisterServer.port);
			this.start();
			this.logUtilsMain.writeLog(LoginRegisterServer.placeholder + "server started.");
		}
		catch(Exception ec)
		{
			this.logUtilsMain.writeLog(LoginRegisterServer.placeholder + "startLRServer(): " + ec);
		}
	}
	
	private static final String KeystoreFilePath = "/usr/server/ECCMasterKey.jks";
	private static final String TrustStoreFilePath = "/usr/server/servertruststore.jks";
	private static final String KeystorePass = "8db3626e47";
	private static final String TruststorePassword = "842407c248";
	private SSLContext sslContext;
	
	private void initSSLKey() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException, UnrecoverableKeyException, KeyManagementException
	{
		this.logUtilsMain.writeLog(LoginRegisterServer.placeholder + "Setting up SSL-Encryption");
		KeyStore trustStore = KeyStore.getInstance("JKS");
		trustStore.load(new FileInputStream(LoginRegisterServer.TrustStoreFilePath), LoginRegisterServer.TruststorePassword.toCharArray());
		this.logUtilsMain.writeLog(LoginRegisterServer.placeholder + "SSL-Encryption TrustStore VALID.");
		KeyStore keystore = KeyStore.getInstance("JKS");
		keystore.load(new FileInputStream(LoginRegisterServer.KeystoreFilePath), LoginRegisterServer.KeystorePass.toCharArray());
		this.logUtilsMain.writeLog(LoginRegisterServer.placeholder + "SSL-Encryption Keystore VALID.");
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(keystore, LoginRegisterServer.KeystorePass.toCharArray());

		TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509"); 
		tmf.init(trustStore);

		sslContext = SSLContext.getInstance("TLS"); 
		TrustManager[] trustManagers = tmf.getTrustManagers(); 
		sslContext.init(kmf.getKeyManagers(), trustManagers, null);
		this.logUtilsMain.writeLog(LoginRegisterServer.placeholder + "SSL-Encryption OK.");
	}
	
	
	private class unfreezeConnections extends TimerTask
	{
	    public void run()
	    {
	    	synchronized(connectionMap)
	    	{
	    		if(connectionMap.size() != 0)
	    		{
	    			logUtilsMain.writeLog(LoginRegisterServer.placeholder + "Clearing IP-HASHMAP");
	    			connectionMap.clear();
	    		}
	    	}
	    }
	}
	
	private static final ThreadPoolExecutor threadPoolMain = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
            100,
            15,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<Runnable>(100),
            new ThreadPoolExecutor.CallerRunsPolicy());
	
	private static final int MAX_CONN_PER_MINUTE = 50;
	@Override
	public void run()
	{
		while(true)
		{
			try
			{
				SSLSocket socket = (SSLSocket) serverSocket.accept();
				if(this.connectionMap.get(socket.getInetAddress().toString()) != null)
				{
					if(this.connectionMap.get(socket.getInetAddress().toString()) >= LoginRegisterServer.MAX_CONN_PER_MINUTE)
					{
						socket.close();
					}
					else
					{
						this.connectionMap.put(socket.getInetAddress().toString(),  this.connectionMap.get(socket.getInetAddress().toString()) + 1);
						this.logUtilsMain.writeLog("Connection: " + socket.getInetAddress());
						
						LoginRegisterServer.threadPoolMain.submit(new RequestHandler(socket));
					}
				}
				else
				{
					this.connectionMap.put(socket.getInetAddress().toString(), 1);
					this.logUtilsMain.writeLog("Connection: " + socket.getInetAddress());
					LoginRegisterServer.threadPoolMain.submit(new RequestHandler(socket));
				}
			}
			catch(Exception ec)
			{
				this.logUtilsMain.writeLog(LoginRegisterServer.placeholder + "(ACCEPT ERROR) " + ec);
			}
		}
	}



	private static final String WELCOME_MESSAGE =
			"Thanks for joining us\uD83D\uDE01\uD83D\uDE1C\n" +
					"My name is Julian, and what is your name?";

	private static final String WELCOME_MESSAGE_SPOT_INFORMATIONS =
			"{\"TDC\":\"\",\"FTA\":1,\"FFY\":\"actionj\",\"FS\":0,\"TS\":20,\"TC\":-14575885,\"BGC\":-5317}";


	private static final String DEMO_ACCOUNT_DESCRIPTION = "{\"FTA\":1,\"FFY\":\"plg\",\"FS\":1,\"TS\":15,\"TC\":-14575885,\"BGC\":-26624, \"TDC\": \"Demo User\"}";


	private static final String cmd_Login = "LRL";
	private static final String cmd_Register = "LRR";
	private static final String cmd_RegisterDemoAccount = "LRRDA";
	private static final String cmd_UsernameCheck = "LRU";
	private static final String cmd_EmailCheck = "LRE";
	private static final String cmd_FCM_CHANGED = "LRFC";
	private static final String cmd_RequestNewPass = "LRNPR";
	private static final String cmd_checkIfEmailNotFull = "LRCEA";
	private static final String cmd_registerOrLoginWithGoogleApi = "LRHGA";
	private static final String reply_LoginSuccessfull = "LRLT";
	private static final String reply_RegisterSuccessfull= "LRRT";
	private static final String reply_UsernameOk = "LRUT";
	private static final String reply_EmailOk = "LRET";
	private static final String reply_LoginFailed = "LRLF";
	private static final String reply_RegisterFailed = "LRRF";
	private static final String reply_UsernameTaken = "LRUF";
	private static final String reply_EmailTaken = "LREF";
	
	private static final String reply_fatalError = "LRERR";
	private static final String reply_emailVerify = "LRVE";
	
	private static final String mann = "male";
	private static final String frau = "female";
	
	
	private static final String queryRegisterUser = 
			"insert into Users (Benutzername, Passwort, PB, Vorname, Nachname, Email, Geburtstag, Region, ProfilPublicity, Description)" +
			" values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	
	private static final String queryCheckAccount = "SELECT Benutzername FROM Users WHERE Benutzername=? LIMIT 1";
	private static final String queryInsertLifeCaptureFriendShip = "INSERT INTO Watcher (UID, FUID, AD, WF) values (1, ?, 0, 0)";
	private static final String queryCheckLogin = "SELECT Benutzername, Passwort FROM Users WHERE Benutzername=? AND Passwort=? AND Deleted=0 LIMIT 1";
	private static final String queryLookUpUID = "SELECT UID FROM Users WHERE Benutzername=? LIMIT 1";
	private static final String queryLookUpUsername = "SELECT Benutzername FROM Users WHERE UID=? LIMIT 1";
	private static final String queryInsertNewPrivateUserPost = "INSERT INTO PrivateMoments(UID, FUID, Beschreibung, PID, ImageHQ, TYPE) VALUES (?, ?, ? ,?, ?, ?)";
	private static final String queryGetHashtagsFromPost = "SELECT TAG_NAME FROM TAGS WHERE PID=?";
	private static final String queryCheckEmailTaken = "SELECT Benutzername, Email FROM Users WHERE Email=? LIMIT 1";
	private static final String queryCheckUsernameTaken = "SELECT Benutzername, Email FROM Users WHERE Benutzername=? LIMIT 1";
	private static final String queryCheckEmailVerificated = "SELECT Username FROM EmailVerification WHERE Username=? LIMIT 1";
	private static final String queryCheckIfNewPasswordRequested = "SELECT Username FROM PasswordResetRequest WHERE Username=? LIMIT 1";

	private static final String queryUpdateFCM = "INSERT INTO FirebaseCloudMessaging (UID, FCM) values (?, ?)";
	private static final String queryDeleteFCM = "DELETE FROM FirebaseCloudMessaging WHERE UID=? LIMIT 1";
	private static final String queryGetFullProfil = "SELECT * FROM Users WHERE Benutzername=? LIMIT 1";



	private class RequestHandler extends Thread
	{
		private LogUtilsEsaph logUtilsRequest;
		private JSONObject jsonMessage;
		private SSLSocket socket;
		private PrintWriter writer;
		private BufferedReader reader;
		private Connection connection;

		private RequestHandler(SSLSocket socket)
		{
			this.socket = socket;
		}

		@Override
		public void run()
		{
			try
			{
				this.logUtilsRequest = new LogUtilsEsaph(new File(LoginRegisterServer.mainServerLogPath),
						LoginRegisterServer.ServerType,
						socket.getInetAddress().getHostAddress(), -1);

				this.socket.setSoTimeout(15000);
				this.writer = new PrintWriter(new OutputStreamWriter(this.socket.getOutputStream(), StandardCharsets.UTF_8), true);
				this.reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8));

				jsonMessage = new JSONObject(this.readDataCarefully(1800));
				this.connection = (Connection) pool.getConnectionFromPool();

				String anfrage = this.jsonMessage.getString("LRC");


				this.logUtilsRequest.writeLog(LoginRegisterServer.placeholder + "Checking client --> " + this.socket.getInetAddress() + " Access granted.");


				if(anfrage.equals(LoginRegisterServer.cmd_Register))
				{
					PreparedStatement st = null;
					ResultSet result = null;
					PreparedStatement userdata = null;
					PreparedStatement prLookUpRegister = null;
					ResultSet lookUpResultRegister = null;
					PreparedStatement prepareInsertLifeCaptureFriend = null;

					try
					{
						String Username = this.jsonMessage.getString("USRN");
						for (int i = 0; i < Username.length(); i++) {
				            int type = Character.getType(Username.charAt(i));
				            if (type == Character.SURROGATE || type == Character.OTHER_SYMBOL)
				            {
				            	return;
				            }
				        }

						this.logUtilsRequest.writeLog(LoginRegisterServer.placeholder + "Client ---> " + this.socket.getInetAddress() + " registering...");

						st = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryCheckAccount);
						st.setString(1, this.jsonMessage.getString("USRN"));
						result = st.executeQuery();

						if(!result.next())
						{
							this.logUtilsRequest.writeLog("Username not registered.");
							String username = this.jsonMessage.getString("USRN");
							String password = this.jsonMessage.getString("PW");
							String vorname = this.jsonMessage.getString("VORNAME");
							String nachname = this.jsonMessage.getString("NACHNAME");
							String email = this.jsonMessage.getString("EMAIL");
							String geschlecht = this.jsonMessage.getString("GESCHLECHT");
							String birthday = this.jsonMessage.getString("BIRTHDAY");
							String region = this.jsonMessage.getString("REGION");
							JSONObject jsonObjectDescriptionPlopp = this.jsonMessage.getJSONObject("DESPLOPP");

							if(checkRegisterData(username, password, vorname, nachname, email, geschlecht, Long.parseLong(birthday), region)
									&& new EsaphJSONPloppValidator().validate(jsonObjectDescriptionPlopp))
							{
								MessageDigest md = MessageDigest.getInstance("SHA-256"); //HASH-FUNKTION
								userdata = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryRegisterUser);
								userdata.setString(1, username);
								md.update(password.getBytes(StandardCharsets.UTF_8));
								//UPDATING HASH
								userdata.setString(2, Arrays.toString(md.digest()));
								userdata.setString(3, "");
								userdata.setString(4, vorname);
								userdata.setString(5, nachname);
								userdata.setString(6, email);
								userdata.setDate(7, new Date(Long.parseLong(birthday)));
								userdata.setString(8, region);
								userdata.setShort(9, (short) 0);
								userdata.setString(10, jsonObjectDescriptionPlopp.toString());
								userdata.executeUpdate();


								prLookUpRegister = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryLookUpUID);
								prLookUpRegister.setString(1, username);
								lookUpResultRegister = prLookUpRegister.executeQuery();
								if(lookUpResultRegister.next())
								{
									long UID = lookUpResultRegister.getLong("UID");
									if(UID > 0)
									{
										prepareInsertLifeCaptureFriend =
												(PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryInsertLifeCaptureFriendShip);
										prepareInsertLifeCaptureFriend.setLong(1, UID);
										prepareInsertLifeCaptureFriend.executeUpdate();
										sentLifeCaptureNewAccountRegistred(username);
										sentWelcomeImage(UID);
									}
								}


								this.logUtilsRequest.writeLog("User registered");
								MailServer mail = new MailServer(this.connection, email, username, this.logUtilsRequest);
								if(mail.generateVIDAndSendEmailRegistration())
								{
									this.writer.println(LoginRegisterServer.reply_RegisterSuccessfull);
								}
							}
							else
							{
								this.writer.println(LoginRegisterServer.reply_RegisterFailed);
							}
						}
					}
					catch(Exception ec)
					{
						this.logUtilsRequest.writeLog("Exception Register: " + ec);
					}
					finally {
						if(st != null)
						{
							st.close();
						}

						if(result != null)
						{
							result.close();
						}

						if(userdata != null)
						{
							userdata.close();
						}

						if(prLookUpRegister != null)
						{
							prLookUpRegister.close();
						}

						if(lookUpResultRegister != null)
						{
							lookUpResultRegister.close();
						}

						if(prepareInsertLifeCaptureFriend != null)
						{
							prepareInsertLifeCaptureFriend.close();
						}
					}
				}
				else if(anfrage.equals(LoginRegisterServer.cmd_registerOrLoginWithGoogleApi))
				{
					PreparedStatement prCheckEmailTaken = null;
					ResultSet resultCheckEmailTaken = null;

					PreparedStatement userdata = null;
					PreparedStatement prLookUpRegister = null;
					ResultSet lookUpResultRegister = null;

					try
					{
						JacksonFactory jsonFactory = new JacksonFactory();

						GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), jsonFactory)
								.setAudience(Collections.singletonList(LoginRegisterServer.GOOGLE_API_ID)) //ID
								.build();

						String username;
						String vorname;
						String email;
						String region;
						JSONObject jsonObjectDescriptionPlopp = this.jsonMessage.getJSONObject("DESPLOPP");

						GoogleIdToken idToken = verifier.verify(this.jsonMessage.getString("IDTS"));
						if (idToken != null)
						{
							Payload payload = idToken.getPayload();
							String userId = payload.getSubject();
							email = payload.getEmail();
							username = (String) payload.get("name");
							if(username == null)
							{
								this.logUtilsRequest.writeLog("USERNAME IS NULL");
							}
							this.logUtilsRequest.writeLog("User ID: " + userId + " USERNAME " + username);
							region = (String) payload.get("locale");
							vorname = (String) payload.get("given_name");

							prCheckEmailTaken = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryCheckEmailTaken);
							prCheckEmailTaken.setString(1, email);
							resultCheckEmailTaken = prCheckEmailTaken.executeQuery();

							if(!resultCheckEmailTaken.next()) //Register new account
							{
								String UsernameCache = username;
								boolean accountExists = true;
								while(accountExists)
								{
									PreparedStatement preparedStatementCheckAccount = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryCheckAccount);
									preparedStatementCheckAccount.setString(1, UsernameCache);
									ResultSet resultCheckAccount = preparedStatementCheckAccount.executeQuery();

									if(resultCheckAccount.next())
									{
										ArrayList<Integer> list = new ArrayList<Integer>();
										for (int i=1; i<11; i++)
										{
											list.add(new Integer(i));
										}
										Collections.shuffle(list);
										UsernameCache = username + list.get(0);
									}
									else
									{
										accountExists = false;
										username = UsernameCache;
									}

									preparedStatementCheckAccount.close();
									resultCheckAccount.close();
								}



								if(checkRegisterData(username, vorname, email, region)
										&& new EsaphJSONPloppValidator().validate(jsonObjectDescriptionPlopp))
								{
									userdata = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryRegisterUser);
									userdata.setString(1, username);
									userdata.setString(2, "N/A");
									userdata.setString(3, "");
									userdata.setString(4, vorname);
									userdata.setString(5, "N/A");
									userdata.setString(6, email);
									Calendar c = new GregorianCalendar();
									c.set(2000, Calendar.JANUARY, 1, 0, 0, 0);

									userdata.setDate(7, new Date(c.getTimeInMillis()));
									userdata.setString(8, region);
									userdata.setShort(9, (short) 0);
									userdata.setString(10, jsonObjectDescriptionPlopp.toString());

									userdata.executeUpdate();

									prLookUpRegister = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryLookUpUID);
									prLookUpRegister.setString(1, username);

									lookUpResultRegister = prLookUpRegister.executeQuery();
									if(lookUpResultRegister.next())
									{
										long UID = lookUpResultRegister.getLong("UID");
										if(UID > 0)
										{
											PreparedStatement prepareInsertLifeCaptureFriend =
													(PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryInsertLifeCaptureFriendShip);
											prepareInsertLifeCaptureFriend.setLong(1, UID);
											prepareInsertLifeCaptureFriend.executeUpdate();
											prepareInsertLifeCaptureFriend.close();
											sentLifeCaptureNewAccountRegistred(username);
											sentWelcomeImage(UID);
										}
									}

									this.logUtilsRequest.writeLog("User registered");
									MailServer mail = new MailServer(this.connection, email, username, this.logUtilsRequest);
									if(mail.generateVIDAndSendEmailRegistration())
									{
										if(!this.jsonMessage.getString("FCMT").equals("NT-1") && !this.jsonMessage.getString("FCMT").isEmpty())
										{
											PreparedStatement prLookUpUid = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryLookUpUID);
											prLookUpUid.setString(1, username);
											ResultSet lookUpResult = prLookUpUid.executeQuery();

											this.logUtilsRequest.writeLog(LoginRegisterServer.placeholder + "FCM Token wurde beim login eingereicht.");
											if(lookUpResult.next() && !this.jsonMessage.getString("FCMT").isEmpty())//Wenn nt-1 der fall ist, heißt das das keine sid aktuallisiert werden muss.
											{
												SessionHandler sessionHandler = new SessionHandler(this.connection, this.logUtilsRequest);
												PreparedStatement FCMDELETE = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryDeleteFCM);
												FCMDELETE.setLong(1, lookUpResult.getLong("UID"));
												FCMDELETE.executeUpdate();
												FCMDELETE.close();

												PreparedStatement FCMUPGRADE = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryUpdateFCM);
												FCMUPGRADE.setLong(1, lookUpResult.getLong("UID"));
												FCMUPGRADE.setString(2, this.jsonMessage.getString("FCMT"));
												FCMUPGRADE.executeUpdate();
												FCMUPGRADE.close();
												this.writer.println(LoginRegisterServer.reply_LoginSuccessfull);

												JSONObject jsonObject = new JSONObject();
												jsonObject.put("SID", sessionHandler.createNewSession(lookUpResult.getLong("UID")));
												jsonObject.put("UID", lookUpResult.getLong("UID"));
												jsonObject.put("USRN", username);
												this.writer.println(jsonObject.toString());
												this.logUtilsRequest.writeLog("UID: OK." + username);
											}
											else
											{
												this.writer.print(LoginRegisterServer.reply_fatalError);
												this.logUtilsRequest.writeLog("UID: WRONG.");
											}

											prLookUpUid.close();
											lookUpResult.close();
										}
										else
										{
											SessionHandler sessionHandler = new SessionHandler(this.connection, this.logUtilsRequest);
											this.logUtilsRequest.writeLog("NO FCM TOKEN.");
											PreparedStatement prLookUpUid = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryLookUpUID);
											prLookUpUid.setString(1, username);
											ResultSet lookUpResultUID = prLookUpUid.executeQuery();
											if(lookUpResultUID.next())
											{
												this.writer.println(LoginRegisterServer.reply_LoginSuccessfull);

												JSONObject jsonObject = new JSONObject();
												jsonObject.put("SID", sessionHandler.createNewSession(lookUpResultUID.getLong("UID")));
												jsonObject.put("UID", lookUpResultUID.getLong("UID"));
												jsonObject.put("USRN", username);
												this.writer.println(jsonObject.toString());
												this.logUtilsRequest.writeLog("UID: OK." + username);
											}
											else
											{
												this.writer.println(LoginRegisterServer.reply_LoginFailed);
											}

											prLookUpUid.close();
											lookUpResultUID.close();
										}
									}
								}
								else
								{
									this.writer.println("-1");
								}
							}
							else
							{
								username = resultCheckEmailTaken.getString("Benutzername");
								if(!this.jsonMessage.getString("FCMT").equals("NT-1") && !this.jsonMessage.getString("FCMT").isEmpty())
								{
									PreparedStatement prLookUpUID = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryLookUpUID);
									prLookUpUID.setString(1, username);
									ResultSet lookUpResultUID = prLookUpUID.executeQuery();

									this.logUtilsRequest.writeLog(LoginRegisterServer.placeholder + "FCM Token wurde beim login eingereicht.");
									if(lookUpResultUID.next()  && !this.jsonMessage.getString("FCMT").isEmpty())//Wenn nt-1 der fall ist, heißt das das keine sid aktuallisiert werden muss.
									{
										SessionHandler sessionHandler = new SessionHandler(this.connection, this.logUtilsRequest);
										PreparedStatement FCMDELETE = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryDeleteFCM);
										FCMDELETE.setLong(1, lookUpResultUID.getLong("UID"));
										FCMDELETE.executeUpdate();
										FCMDELETE.close();

										PreparedStatement FCMUPGRADE = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryUpdateFCM);
										FCMUPGRADE.setLong(1, lookUpResultUID.getLong("UID"));
										FCMUPGRADE.setString(2, this.jsonMessage.getString("FCMT"));
										FCMUPGRADE.executeUpdate();
										FCMUPGRADE.close();
										this.writer.println(LoginRegisterServer.reply_LoginSuccessfull);
										this.writer.println(sessionHandler.createNewSession(lookUpResultUID.getLong("UID")));
										this.writer.println(username);
										this.logUtilsRequest.writeLog("UID: OK." + username);
									}
									else
									{
										this.writer.print(LoginRegisterServer.reply_fatalError);
										this.logUtilsRequest.writeLog("UID: WRONG.");
									}
									prLookUpUID.close();
									lookUpResultUID.close();
								}
								else
								{
									SessionHandler sessionHandler = new SessionHandler(this.connection, this.logUtilsRequest);
									this.logUtilsRequest.writeLog("NO FCM TOKEN.");
									PreparedStatement prLookUp = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryLookUpUID);
									prLookUp.setString(1, username);
									ResultSet lookUpResult = prLookUp.executeQuery();
									if(lookUpResult.next())
									{
										this.writer.println(LoginRegisterServer.reply_LoginSuccessfull);
										this.writer.println(sessionHandler.createNewSession(lookUpResult.getLong("UID")));
										this.writer.println(username);
										this.logUtilsRequest.writeLog("UID OK: " + username);
									}
									else
									{
										this.writer.println(LoginRegisterServer.reply_LoginFailed);
									}

									prLookUp.close();
									lookUpResult.close();
								}
							}
							prCheckEmailTaken.close();
							resultCheckEmailTaken.close();

						}
						else
						{
							this.logUtilsRequest.writeLog("Invalid ID token.");
							this.writer.println(LoginRegisterServer.reply_LoginFailed);
						}
					}
					catch (Exception ec)
					{
					}
					finally
					{
						if(prCheckEmailTaken != null)
						{
							prCheckEmailTaken.close();
						}

						if(resultCheckEmailTaken != null)
						{
							resultCheckEmailTaken.close();
						}

						if(userdata != null)
						{
							userdata.close();
						}

						if(prLookUpRegister != null)
						{
							prLookUpRegister.close();
						}

						if(lookUpResultRegister != null)
						{
							lookUpResultRegister.close();
						}
					}
				}



				else if(anfrage.equals(LoginRegisterServer.cmd_Login))
				{
					this.logUtilsRequest.writeLog("Client. " + this.socket.getInetAddress() + " logging in...");

					/*
					PreparedStatement checkVerification = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryCheckEmailVerificated);
					checkVerification.setString(1, this.jsonMessage.getString("USRN"));
					ResultSet vresult = checkVerification.executeQuery();
					*/
					if(true)
					{
						MessageDigest md = MessageDigest.getInstance("SHA-256"); //HASH-FUNKTION
						PreparedStatement loginData = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryCheckLogin);
						md.update(this.jsonMessage.getString("PW").getBytes(StandardCharsets.UTF_8));
						//UPDATING HASH
						loginData.setString(1, this.jsonMessage.getString("USRN"));
						loginData.setString(2, Arrays.toString(md.digest()));

						ResultSet result = loginData.executeQuery();

						if(result.next())
						{
							this.logUtilsRequest.writeLog("Passwort OK.");
							if(!this.jsonMessage.getString("FCMT").equals("NT-1") && !this.jsonMessage.getString("FCMT").isEmpty())
							{
								PreparedStatement prLookUp = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryLookUpUID);
								prLookUp.setString(1, this.jsonMessage.getString("USRN"));
								ResultSet lookUpResult = prLookUp.executeQuery();

								this.logUtilsRequest.writeLog(LoginRegisterServer.placeholder + "FCM Token wurde beim login eingereicht.");
								if(lookUpResult.next()  && !this.jsonMessage.getString("FCMT").isEmpty())//Wenn nt-1 der fall ist, heißt das das keine sid aktuallisiert werden muss.
								{
									SessionHandler sessionHandler = new SessionHandler(this.connection, this.logUtilsRequest);
									PreparedStatement FCMDELETE = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryDeleteFCM);
									FCMDELETE.setLong(1, lookUpResult.getLong("UID"));
									FCMDELETE.executeUpdate();
									FCMDELETE.close();

									PreparedStatement FCMUPGRADE = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryUpdateFCM);
									FCMUPGRADE.setLong(1, lookUpResult.getLong("UID"));
									FCMUPGRADE.setString(2, this.jsonMessage.getString("FCMT"));
									FCMUPGRADE.executeUpdate();
									FCMUPGRADE.close();

									this.writer.println(LoginRegisterServer.reply_LoginSuccessfull);

									JSONObject jsonObject = new JSONObject();
									jsonObject.put("SID", sessionHandler.createNewSession(lookUpResult.getLong("UID")));
									jsonObject.put("UID", lookUpResult.getLong("UID"));
									this.writer.println(jsonObject.toString());
								}
								else
								{
									this.writer.print(LoginRegisterServer.reply_fatalError);
									this.logUtilsRequest.writeLog("UID: WRONG.");
								}
								prLookUp.close();
								lookUpResult.close();
							}
							else
							{
								SessionHandler sessionHandler = new SessionHandler(this.connection, this.logUtilsRequest);
								this.logUtilsRequest.writeLog("NO FCM TOKEN.");
								PreparedStatement prLookUp = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryLookUpUID);
								prLookUp.setString(1, this.jsonMessage.getString("USRN"));
								ResultSet lookUpResult = prLookUp.executeQuery();
								if(lookUpResult.next())
								{
									this.writer.println(LoginRegisterServer.reply_LoginSuccessfull);

									JSONObject jsonObject = new JSONObject();
									jsonObject.put("SID", sessionHandler.createNewSession(lookUpResult.getLong("UID")));
									jsonObject.put("UID", lookUpResult.getLong("UID"));
									this.writer.println(jsonObject.toString());
								}
								else
								{
									this.writer.println(LoginRegisterServer.reply_LoginFailed);
								}

								prLookUp.close();
								lookUpResult.close();
							}
						}
						else
						{
							this.logUtilsRequest.writeLog("Falsches Passwort.");
							this.writer.println(LoginRegisterServer.reply_LoginFailed);
						}
						loginData.close();
						result.close();
					}
					else
					{
						this.writer.print(LoginRegisterServer.reply_emailVerify); //Email verfify
					}
					//checkVerification.close();
					//vresult.close();
				}




				else if(anfrage.equals(LoginRegisterServer.cmd_FCM_CHANGED))
				{
					PreparedStatement prLookUp = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryLookUpUID);
					prLookUp.setString(1, this.jsonMessage.getString("USRN"));
					ResultSet lookUpResult = prLookUp.executeQuery();
					if(lookUpResult.next())
					{
						PreparedStatement FCMDELETE = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryDeleteFCM);
						FCMDELETE.setLong(1, lookUpResult.getLong("UID"));
						FCMDELETE.executeUpdate();
						FCMDELETE.close();

						PreparedStatement FCMUPGRADE = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryUpdateFCM);
						FCMUPGRADE.setLong(1, lookUpResult.getLong("UID"));
						FCMUPGRADE.setString(2, this.jsonMessage.getString("FCMT"));
						FCMUPGRADE.executeUpdate();
						FCMUPGRADE.close();
					}

					prLookUp.close();
					lookUpResult.close();
				}




				else if(anfrage.equals(LoginRegisterServer.cmd_EmailCheck))
				{
					this.logUtilsRequest.writeLog("Checking mail..");
					PreparedStatement pr = null;
					ResultSet result = null;

					try
					{
						String email = this.jsonMessage.getString("EMAIL");
						pr = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryCheckEmailTaken);
						pr.setString(1, email);
						result = pr.executeQuery();
						if(!result.next())
						{
							this.writer.println(LoginRegisterServer.reply_EmailOk);
						}
						else
						{
							this.writer.println(LoginRegisterServer.reply_EmailTaken);
						}
					}
					catch(Exception ec)
					{
						this.logUtilsRequest.writeLog("Exception(Checking mail): " + ec);
						this.writer.println(LoginRegisterServer.reply_fatalError);
					}
					finally
					{
						if(pr != null)
						{
							pr.close();
						}

						if(result != null)
						{
							result.close();
						}
					}
				}
				else if(anfrage.equals(LoginRegisterServer.cmd_UsernameCheck))
				{
					PreparedStatement pr = null;
					ResultSet result = null;

					try
					{
						String username = this.jsonMessage.getString("USRN");
						pr = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryCheckUsernameTaken);
						pr.setString(1, username);
						result = pr.executeQuery();
						if(!result.next())
						{
							this.writer.println(LoginRegisterServer.reply_UsernameOk);
						}
						else
						{
							this.writer.println(LoginRegisterServer.reply_UsernameTaken);
						}
					}
					catch(Exception ec)
					{
						this.logUtilsRequest.writeLog("Exception(Checking username): " + ec);
						try
						{
							this.writer.println(LoginRegisterServer.reply_fatalError);
						}
						catch (Exception ec1)
						{

						}

					}
					finally
					{
						if(pr != null)
						{
							pr.close();
						}

						if(result != null)
						{
							result.close();
						}
					}
				}
				else if(anfrage.equals(LoginRegisterServer.cmd_RequestNewPass))
				{
					PreparedStatement pr = null;
					ResultSet result = null;

					try
					{
						this.logUtilsRequest.writeLog("Sending mail ");
						String Username = this.jsonMessage.getString("USRN");
						pr = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryCheckIfNewPasswordRequested);
						pr.setString(1, Username);
						result = pr.executeQuery();
						if(!result.next())
						{
							this.logUtilsRequest.writeLog("Sending mail next");
							PreparedStatement prCheckUsername = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryCheckUsernameTaken);
							prCheckUsername.setString(1, Username);
							ResultSet resultCheckUsername = prCheckUsername.executeQuery();
							if(resultCheckUsername.next()) //ACCOUNT EXISTS
							{
								String email = resultCheckUsername.getString("Email");
								this.logUtilsRequest.writeLog("Sending mail next: " + email);

								if(!email.isEmpty())
								{
									MailServer m = new MailServer(this.connection, email, Username, this.logUtilsRequest);
									if(m.SendEmailRegistration())
									{
										this.writer.println("ES");
									}
								}
							}
							prCheckUsername.close();
							resultCheckUsername.close();
						}
						else
						{
							this.writer.println("AR");
						}
					}
					catch(Exception ec)
					{
						this.logUtilsRequest.writeLog("Exception(Request new password): " + ec);
					}
					finally
					{
						if(pr != null)
						{
							pr.close();
						}

						if(result != null)
						{
							result.close();
						}
					}
				}
				else if(anfrage.equals(LoginRegisterServer.cmd_checkIfEmailNotFull))
				{
					try
					{
						final int check = 150 - MailServer.getEmailLimit();
						if(check > 0)
						{
							this.writer.println("1");
						}
						else
						{
							this.writer.println("0");
						}
					}
					catch(Exception ec)
					{
						this.logUtilsRequest.writeLog("Exception(Check if mail sent limit reached) failed: " + ec);
					}
				}
				else if(anfrage.equals(LoginRegisterServer.cmd_RegisterDemoAccount))
				{
					PreparedStatement st = null;
					ResultSet result = null;
					PreparedStatement userdata = null;
					PreparedStatement prLookUpRegister = null;
					ResultSet lookUpResultRegister = null;
					PreparedStatement prepareInsertLifeCaptureFriend = null;

					try
					{
						String Username = this.jsonMessage.getString("USRN");
						for (int i = 0; i < Username.length(); i++)
						{
							int type = Character.getType(Username.charAt(i));
							if (type == Character.SURROGATE || type == Character.OTHER_SYMBOL)
							{
								return;
							}
						}

						this.logUtilsRequest.writeLog(LoginRegisterServer.placeholder + "Client ---> " + this.socket.getInetAddress() + " registering test account...");

						st = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryCheckAccount);
						st.setString(1, this.jsonMessage.getString("USRN"));
						result = st.executeQuery();

						if(!result.next())
						{
							Calendar c = new GregorianCalendar();
							c.set(2000, Calendar.JANUARY, 1, 0, 0, 0);
							this.logUtilsRequest.writeLog("Username not registered.");
							String username = this.jsonMessage.getString("USRN");
							String password = this.jsonMessage.getString("PW");
							String vorname = "N/A";
							String nachname = "N/A";
							String email = "N/A@N/A.spotlight";
							String geschlecht = LoginRegisterServer.mann;
							String region = "00";

							MessageDigest md = MessageDigest.getInstance("SHA-256"); //HASH-FUNKTION
							userdata = (PreparedStatement) this.connection.prepareStatement(queryRegisterUser);
							userdata.setString(1, username);
							md.update(password.getBytes(StandardCharsets.UTF_8));
							//UPDATING HASH
							userdata.setString(2, Arrays.toString(md.digest()));
							userdata.setString(3, "");
							userdata.setString(4, vorname);
							userdata.setString(5, nachname);
							userdata.setString(6, email);
							userdata.setDate(7, new Date(c.getTimeInMillis()));
							userdata.setString(8, region);
							userdata.setShort(9, (short) 0);
							userdata.setString(10, LoginRegisterServer.DEMO_ACCOUNT_DESCRIPTION);
							userdata.executeUpdate();

							prLookUpRegister = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryLookUpUID);
							prLookUpRegister.setString(1, username);
							lookUpResultRegister = prLookUpRegister.executeQuery();
							if(lookUpResultRegister.next())
							{
								long UID = lookUpResultRegister.getLong("UID");
								if(UID > 0)
								{
									PreparedStatement FCMDELETE = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryDeleteFCM);
									FCMDELETE.setLong(1, UID);
									FCMDELETE.executeUpdate();
									FCMDELETE.close();

									PreparedStatement FCMUPGRADE = (PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryUpdateFCM);
									FCMUPGRADE.setLong(1, UID);
									FCMUPGRADE.setString(2, this.jsonMessage.getString("FCMT"));
									FCMUPGRADE.executeUpdate();
									FCMUPGRADE.close();

									prepareInsertLifeCaptureFriend =
											(PreparedStatement) this.connection.prepareStatement(LoginRegisterServer.queryInsertLifeCaptureFriendShip);
									prepareInsertLifeCaptureFriend.setLong(1, UID);
									prepareInsertLifeCaptureFriend.executeUpdate();
									sentLifeCaptureNewAccountRegistred(username);
									sentWelcomeImage(UID);

									this.writer.println(LoginRegisterServer.reply_RegisterSuccessfull);

									SessionHandler sessionHandler = new SessionHandler(this.connection, this.logUtilsRequest);
									JSONObject jsonObject = new JSONObject();
									jsonObject.put("SID", sessionHandler.createNewSession(UID));
									jsonObject.put("UID", UID);
									jsonObject.put("USRN", username);
									this.writer.println(jsonObject.toString());

									this.logUtilsRequest.writeLog("Test User registered");
								}
							}
						}
					}
					catch(Exception ec)
					{
						this.logUtilsRequest.writeLog("Exception Register Test User: " + ec);
					}
					finally {
						if(st != null)
						{
							st.close();
						}

						if(result != null)
						{
							result.close();
						}

						if(userdata != null)
						{
							userdata.close();
						}

						if(prLookUpRegister != null)
						{
							prLookUpRegister.close();
						}

						if(lookUpResultRegister != null)
						{
							lookUpResultRegister.close();
						}

						if(prepareInsertLifeCaptureFriend != null)
						{
							prepareInsertLifeCaptureFriend.close();
						}
					}
				}


				this.writer.close();
				this.reader.close();
				this.socket.close();
				this.logUtilsRequest.writeLog("Client: " + this.socket.getInetAddress() + " connection closed.");
			}
			catch(Exception etc)
			{
				this.logUtilsRequest.writeLog(LoginRegisterServer.placeholder + "Exception: " + etc);
				try
				{
					this.writer.close();
					this.socket.close();
					this.reader.close();
				}
				catch(Exception ec)
				{
					this.logUtilsRequest.writeLog(LoginRegisterServer.placeholder + "force closing error: " + ec);
				}
			}
			finally
			{
				this.connection = (Connection) pool.returnConnectionToPool(this.connection);
			}
		}

		public long lookUpUID(String username)
		{
			PreparedStatement prLookUp = null;
			ResultSet lookUpResult = null;
			try
			{
				this.logUtilsRequest.writeLog("USERNAME:" + username);
				prLookUp = (PreparedStatement) this.connection
						.prepareStatement(LoginRegisterServer.queryLookUpUID);
				prLookUp.setString(1, username);

				lookUpResult = prLookUp.executeQuery();

				long UID = -1;

				if (lookUpResult.next())
				{
					this.logUtilsRequest.writeLog("UID OK.");
					UID = lookUpResult.getLong(1);
				}
				else {
					this.logUtilsRequest.writeLog("UID WRONG.");
				}

				return UID;

			}
			catch (Exception ec)
			{
				this.logUtilsRequest.writeLog("(lookUpUID): FATAL ERROR: " + ec);
				return -1;
			}
			finally
			{
				try
				{
					if(prLookUp != null)
					{
						prLookUp.close();
					}

					if(lookUpResult != null)
					{
						lookUpResult.close();
					}
				}
				catch (Exception ec)
				{
				}
			}
		}

		public String lookUpUsername(long UID) throws SQLException
		{
			PreparedStatement pr = null;
			ResultSet result = null;

			try
			{
				pr = (PreparedStatement) this.connection
						.prepareStatement(LoginRegisterServer.queryLookUpUsername);
				pr.setLong(1, UID);
				result = pr.executeQuery();

				String Username = null;

				if (result.next())
				{
					Username = result.getString("Benutzername");
				}

				if(Username == null)
				{
					throw new SQLException("Benutzername nicht gefunden");
				}

				return Username;
			}
			catch (Exception ec)
			{
				return null;
			}
			finally
			{
				if(pr != null)
				{
					pr.close();
				}

				if(result != null)
				{
					result.close();
				}
			}
		}

		private void sentWelcomeImage(long uidEmpfanger)
		{
			try
			{
				long timeSent = System.currentTimeMillis();
				EsaphInternalMessageCreator json = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_UserSendTextMessageInPrivate,
						uidEmpfanger);

				json.putInto("USRN", 1);
				json.putInto("MSG", LoginRegisterServer.WELCOME_MESSAGE);
				json.putInto("PLINF", LoginRegisterServer.WELCOME_MESSAGE_SPOT_INFORMATIONS);
				json.putInto("TIME", timeSent);
				json.putInto("MH",
						UUID.nameUUIDFromBytes((LoginRegisterServer.WELCOME_MESSAGE + timeSent).getBytes())
						.getMostSignificantBits());

				LoginRegisterServer.threadPoolMain.submit(new SendInformationToUser(json.getJSON(), this.logUtilsRequest, LoginRegisterServer.this));

			}
			catch (Exception ec)
			{
				this.logUtilsRequest.writeLog("sentWelcomeImage() failed: " + ec);
			}
		}

		private void sentLifeCaptureNewAccountRegistred(String UsernameRegisterd)
		{
			PreparedStatement preparedStatementGetProfil = null;
			ResultSet result = null;
			try
			{
				EsaphInternalMessageCreator json = new EsaphInternalMessageCreator(MessageTypeIdentifier.CMD_FriendStatus,
						1); //1 For LifeCapture ID.


				json.putInto("USRN", UsernameRegisterd);
				json.putInto("FST", ServerPolicy.POLICY_DETAIL_CASE_FRIENDS);

				preparedStatementGetProfil = (PreparedStatement) this.connection
						.prepareStatement(LoginRegisterServer.queryGetFullProfil);
				preparedStatementGetProfil.setString(1, UsernameRegisterd);

				result = preparedStatementGetProfil.executeQuery();
				if (result.next())
				{
					JSONObject singleFriend = new JSONObject();
					singleFriend.put("Benutzername", result.getString("Benutzername"));
					singleFriend.put("Vorname", result.getString("Vorname"));
					singleFriend.put("Geburtstag", result.getTimestamp("Geburtstag").getTime());
					singleFriend.put("Region", result.getString("Region"));
					json.putInto("PF", singleFriend);
				}

				threadPoolMain.submit(new SendInformationToUser(json.getJSON(), this.logUtilsRequest, LoginRegisterServer.this));
			}
			catch (Exception ec)
			{
				this.logUtilsRequest.writeLog("Fail to send information to master, that a new user has been registred: " + ec);
			}
			finally
			{
				try
				{
					if(preparedStatementGetProfil != null)
					{
						preparedStatementGetProfil.close();
					}

					if(result != null)
					{
						result.close();
					}
				}
				catch (Exception e)
				{
				}
			}
		}
		
		
		private boolean checkRegisterData(String username, String password, String vorname, String nachname, String email, String geschlecht, long geburtsdatum, String region)
		{
			this.logUtilsRequest.writeLog("Checking Register Data...");
			if(username.length() >= 2 && username.length() <= 30)
			{
				this.logUtilsRequest.writeLog("Username ok.");
				if(password.length() >= 10 && password.length() <= 20)
				{
					this.logUtilsRequest.writeLog("Password ok.");
					if(vorname.length() != 0 && vorname.length() <= 50)
					{
						this.logUtilsRequest.writeLog("Vorname ok.");
						if(nachname.length() != 0 && nachname.length() <= 50)
						{
							this.logUtilsRequest.writeLog("Nachname ok.");
							if(isValidEmailAddress(email))
							{
								this.logUtilsRequest.writeLog("Email ok.");
								if(isBirthdayValid(geburtsdatum))
								{
									this.logUtilsRequest.writeLog("Geburtstag ok.");
									if(geschlecht.equals(LoginRegisterServer.mann) || geschlecht.equals(LoginRegisterServer.frau))
									{
										this.logUtilsRequest.writeLog("Geschlecht ok.");
										if(region.length() <= 3)
										{
											this.logUtilsRequest.writeLog("Region ok.");
											return true;
										}
									}
								}
							}
						}
					}
				}
			}
			return false;
		}
		
		
		
		private boolean checkRegisterData(String username, String vorname, String email, String region)
		{
			this.logUtilsRequest.writeLog("Checking Register Data...");
			if(username.length() >= 2 && username.length() <= 30)
			{
				this.logUtilsRequest.writeLog("Username ok.");
				if(vorname.length() != 0 && vorname.length() <= 50)
				{
					this.logUtilsRequest.writeLog("Vorname ok.");
					if(isValidEmailAddress(email))
					{
						this.logUtilsRequest.writeLog("Email ok.");
						if(region.length() <= 3)
						{
							this.logUtilsRequest.writeLog("Region ok.");
							return true;
						}
					}
				}
			}
			return false;
		}
		
		
		private boolean isValidEmailAddress(String email)
		 {
			 if(email.length() <= 30)
			 {
				     String ePattern = "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$";
				     java.util.regex.Pattern p = java.util.regex.Pattern.compile(ePattern);
				     java.util.regex.Matcher m = p.matcher(email);
				     return m.matches();
			 }
			 else
			 {
				 return false;
			 }
	     }
		 
		 
		 private static final int minAge = 12;
		 private static final String dateFormat = "yyyy-MM-dd";
		 private boolean isBirthdayValid(long gbmillis)
		 {
			 SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
			 Calendar now = Calendar.getInstance();
			 Calendar gb = Calendar.getInstance();
			 gb.setTimeInMillis(gbmillis);
			 now.setTimeInMillis(System.currentTimeMillis());
			 DateTime dt1 = new DateTime(sdf.format(gb.getTime()));
			 DateTime dt2 = new DateTime(sdf.format(now.getTime()));
			 
			 if(gb.getTimeInMillis() < now.getTimeInMillis())
			 {
				 int jahre = Years.yearsBetween(dt1, dt2).getYears();
				 this.logUtilsRequest.writeLog("Years: " + jahre);
				 if(jahre >= minAge && jahre <= 100)
				 {
					 return true;
				 }
				 else
				 {
					 return false;
				 }
			 }
			 else
			 {
				 return false;
			 }
		 }
		 
		 
		 private String readDataCarefully(int bufferSize) throws Exception
			{
				String msg = this.reader.readLine();
				if(msg == null || msg.length() > bufferSize)
				{
					throw new Exception("Exception: msg " + msg + " length: " + msg.length() + ">" + bufferSize);
				}
				return msg;
			}
	}
}



