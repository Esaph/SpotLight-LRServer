/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Esaph;

import org.json.JSONObject;

public class EsaphJSONPloppValidator
{
    public boolean validate(JSONObject jsonObject)
    {
        // TODO: 02.06.2019 validate
        return true;

        /*
        try
        {
            boolean isValid = true;
            int colorBackground = jsonObject.getInt(EsaphPloppJsonKeysHelper.KEY_BACKGROUND_COLOR);
            int colorText = jsonObject.getInt(EsaphPloppJsonKeysHelper.KEY_TEXT_COLOR);
            int textSize = jsonObject.getInt(EsaphPloppJsonKeysHelper.KEY_TEXT_SIZE);

            if(textSize < EsaphPloppJsonKeysHelper.MIN_TEXT_SIZE && textSize > EsaphPloppJsonKeysHelper.MAX_TEXT_SIZE)
            {
                isValid = false;
            }

            return false;
        }
        catch (Exception ec)
        {
            return false;
        }*/
    }
}
