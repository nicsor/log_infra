/* crashinfo - Buildid manages device build detailed signature
 *
 * Copyright (C) Intel 2012
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Nicolas Benoit <nicolasx.benoit@intel.com>
 */

package com.intel.commands.crashinfo.subcommand;

import com.intel.commands.crashinfo.ISubCommand;
import com.intel.commands.crashinfo.option.OptionData;
import com.intel.commands.crashinfo.option.Options;
import com.intel.commands.crashinfo.option.Options.Multiplicity;

import android.os.SystemProperties;
import android.util.Log;

public class BuildId implements ISubCommand {
	String[] myArgs;
	Options myOptions;

	@Override
	public int execute() {
		OptionData mainOp = myOptions.getMainOption();
		if (mainOp == null){
			generateBuildSignature();
		}else if (mainOp.getKey().equals("--spec")){
			generateSpec();
		}else if (mainOp.getKey().equals(Options.HELP_COMMAND)){
			myOptions.generateHelp();
			return 0;
		}else{
			System.err.println("error : unknown op - " + mainOp.getKey());
			return -1;
		}
		return 0;
	}

	private void generateBuildSignature(){
		String sBuildId = android.os.Build.VERSION.INCREMENTAL;
		String sFingerPrint = android.os.Build.FINGERPRINT;
		String sKernelVersion = getProperty("sys.kernel.version");
		String sBuildUserHostname = getProperty("ro.build.user")+"@"+getProperty("ro.build.host");
		String sModemVersion = getProperty("gsm.version.baseband");
		String sIfwiVersion = getProperty("sys.ifwi.version");
		String sIafwVersion = getProperty("sys.ia32.version");
		String sScufwVersion = getProperty("sys.scu.version");
		String sPunitVersion = getProperty("sys.punit.version");
		String sValhooksVersion = getProperty("sys.valhooks.version");

		String sSeparator = ",";
		String sCompleteBuildId = sBuildId + sSeparator
				+ sFingerPrint + sSeparator
				+ sKernelVersion + sSeparator
				+ sBuildUserHostname + sSeparator
				+ sModemVersion + sSeparator
				+ sIfwiVersion + sSeparator
				+ sIafwVersion + sSeparator
				+ sScufwVersion + sSeparator
				+ sPunitVersion + sSeparator
				+ sValhooksVersion;
		System.out.println(sCompleteBuildId);
	}

	private void generateSpec(){

		StringBuffer spec = new StringBuffer();
		spec.append("Build signature is composed of :\n");
		spec.append("BuildId\n");
		spec.append("FingerPrint\n");
		spec.append("KernelVersion\n");
		spec.append("BuildUserHostname\n");
		spec.append("ModemVersion\n");
		spec.append("IfwiVersion\n");
		spec.append("IafwVersion\n");
		spec.append("ScufwVersion\n");
		spec.append("PunitVersion\n");
		spec.append("ValhooksVersion\n");

		System.out.println(spec.toString());
	}

	private String getProperty(String name) {
		try {
			String property = SystemProperties.get(name, "");
			return property;
		} catch (IllegalArgumentException e) {
			Log.w("crashinfo","Propery not available : "+name);
		}
		return "";
	}

	@Override
	public void setArgs(String[] subArgs) {
		myArgs = subArgs;
		myOptions = new Options(subArgs, "Buildid gives the build signature of the device");
		myOptions.addMainOption("--spec", "-s", "", false, Multiplicity.ZERO_OR_ONE, "Displays specification of build signature");
	}

	@Override
	public boolean checkArgs() {
		return myOptions.check();
	}
}
