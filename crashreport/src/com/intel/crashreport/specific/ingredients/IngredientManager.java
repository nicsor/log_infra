/* Copyright (C) 2019 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.intel.crashreport.specific.ingredients;

import com.intel.crashreport.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.os.SystemProperties;

import org.json.JSONObject;
import org.json.JSONException;

public enum IngredientManager {
	INSTANCE;

	private static final String ING_CONF_FILE_PATH = "/system/vendor/etc/ingredients.conf";

	private boolean bNeedRefresh = true;
	private JSONObject lastIngredients = null;
	List<String> sUniqueKeyList = new ArrayList<String>();


	public List<String> getUniqueKeyList() {
		if (bNeedRefresh){
			refreshUniqueKey();
		}
		return sUniqueKeyList;
	}

	private void refreshUniqueKey(){
		// test conf file exits
		File confFile = new File(ING_CONF_FILE_PATH);
		if (!confFile.exists()){
			//no file => no refresh
			bNeedRefresh = false;
			return;
		}
		sUniqueKeyList.clear();
		IngredientBuilder builder_unique = new FileIngredientBuilder(ING_CONF_FILE_PATH);
		// Retrieve the ingredients
		JSONObject ingredients_unique = builder_unique.getIngredients();
		for(String key : ingredients_unique.keySet()) {
			// Add each ingredient value to the JSON string
			String value = null;
			try {
				value = ingredients_unique.getString(key);
			} catch (JSONException e) {
				Log.e("Could not retrieve value from ingredients");
			}
			if(value != null) {
				if (value.equalsIgnoreCase("true")) {
					sUniqueKeyList.add(key);
				}
			}
		}
		//string updated, no need to refresh
		bNeedRefresh = false;
	}

	public List<String> parseUniqueKey(String aKey) {
		List<String> resultList = new ArrayList<String>();
		String filteredKey = aKey.replaceAll("\\[", "" );
		filteredKey = filteredKey.replaceAll("\\]", "" );
		String[] tmpList = filteredKey.split(", ");
		for (String retval:tmpList) {
			resultList.add(retval);
		}
		return resultList;
	}

	public void storeLastIngredients(JSONObject aIng) {
		lastIngredients = aIng;
	}

	public JSONObject getLastIngredients() {
		return lastIngredients;
	}

	public JSONObject getDefaultIngredients() {
		JSONObject ingredients = new JSONObject();
		try {
			ingredients.put("ifwi", SystemProperties.get("sys.ifwi.version"));
			ingredients.put("pmic", SystemProperties.get("sys.pmic.version"));
			ingredients.put("punit", SystemProperties.get("sys.punit.version"));
			ingredients.put("modem", SystemProperties.get("gsm.version.baseband"));
		} catch (JSONException e) {
			Log.e("Could not set up default ingredients");
		}
		return ingredients;
	}

	public String getIngredient(String key) {
		if (lastIngredients == null)
			return null;
		try {
			return lastIngredients.getString(key);
		} catch (JSONException e) {
			return null;
		}
	}

	public void refreshIngredients() {
		//need to update lastingredients
		IngredientBuilder builder = new FileIngredientBuilder(com.intel.crashreport.specific.Build.INGREDIENTS_FILE_PATH);
		// Retrieve the ingredients
		JSONObject ingredients = builder.getIngredients();
		IngredientManager.INSTANCE.storeLastIngredients(ingredients);
	}

        public boolean isIngredientEnabled() {
                // test conf file exits
                File confFile = new File(ING_CONF_FILE_PATH);
                if (confFile.exists()){
                        //no file => consider ingredient disabled
                        return true;
                }
                return false;
        }
}
