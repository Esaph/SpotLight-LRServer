/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

import Esaph.LogUtilsEsaph;

import java.io.IOException;

public class LRServer
{
	public static void main(String[] args) throws IOException
	{
		if(args.length > 0)
		{
			LogUtilsEsaph.logInConsole = Boolean.parseBoolean(args[0]);
		}

		LoginRegisterServer lr = new LoginRegisterServer();
		lr.startLRServer();
	}
}
