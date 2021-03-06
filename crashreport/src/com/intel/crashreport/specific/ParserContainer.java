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

package com.intel.crashreport.specific;

import android.content.Context;
import android.content.res.AssetManager;

import com.intel.crashreport.Log;
import com.intel.parsing.ParserDirector;

public enum ParserContainer {
	INSTANCE;

	private ParserDirector mDirector = null;

	public void initDirector(Context aContext){
		Log.i("CrashReport: init of parser container");
		mDirector = new ParserDirector();
		mDirector.initParserWithManager(aContext.getAssets());
		int iParserCount = mDirector.getParserCount();
		Log.i("CrashReport: " + iParserCount + " parser(s) found" );
	}

	public boolean parseEvent(Event aEvent){
		if (mDirector != null){
			return mDirector.parseEvent(aEvent.getParsableEvent());
		} else {
			Log.e("CrashReport: mDirector is null");
			return false;
		}
	}
}
