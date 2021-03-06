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

package com.intel.commands.crashinfo.subcommand;

import java.io.IOException;

import java.util.concurrent.TimeUnit;

import com.intel.commands.crashinfo.CrashInfo;
import com.intel.commands.crashinfo.DBManager;
import com.intel.commands.crashinfo.ISubCommand;
import com.intel.commands.crashinfo.option.OptionData;
import com.intel.commands.crashinfo.option.Options;
import com.intel.commands.crashinfo.option.Options.Multiplicity;

import android.os.SystemProperties;

public class Status implements ISubCommand {

	public static final String PATH_LOGS = SystemProperties.get("persist.vendor.crashlogd.root", "/logs") + "/";
	public static final String PATH_SD_LOGS = "/mnt/sdcard/logs";
	String[] myArgs;
	Options myOptions;

	public Status(){

	}

	@Override
	public int execute() throws IOException{
		OptionData mainOp = myOptions.getMainOption();
		if (mainOp == null){
			return execBaseStatus();
		}else if (mainOp.getKey().equals("--uptime")){
			return execUptime();
		}else if (mainOp.getKey().equals(Options.HELP_COMMAND)){
			myOptions.generateHelp();
			return 0;
		}else{
			System.out.println("error : unknown op - " + mainOp.getKey());
			return -1;
		}
	}


	private int execUptime() throws IOException{
		DBManager aDB = new DBManager();
		if (!aDB.isOpened()){
			throw new IOException("Database not opened!");
		}

		long duration = aDB.getCurrentUptime() ;
		aDB.close();
		if (duration >=0){
			long seconds = TimeUnit.SECONDS.toSeconds(duration) % 60;
			long days = TimeUnit.SECONDS.toDays(duration);
			long hours = TimeUnit.SECONDS.toHours(duration)% 24;
			long minutes = TimeUnit.SECONDS.toMinutes(duration) % 60;
			System.out.println( "Uptime since the last software update (SWUPDATE event):");
			if (days > 0) {
				System.out.println( days + " days");
			}
			if (hours > 0) {
				System.out.println( hours + " hours");
			}
			if (minutes > 0) {
				System.out.println( minutes + " minutes");
			}
			System.out.println(seconds + " seconds");
		}else{
			System.out.println("Error : No UPTIME found");
		}

		return 0;
	}

	private int execBaseStatus(){
		try {
			displayDbstatus();
			System.out.println("Main Path for logs : " + PATH_LOGS);
			System.out.println("Api version : "  + CrashInfo.API_VERSION);
			System.out.println("Organization : "  + com.intel.crashtoolserver.bean.Build.DEFAULT_ORGANIZATION);
		}catch (Exception e) {
			System.out.println("Exception : "+e.toString());
			return -1;
		}
		return 0;
	}

	@Override
	public void setArgs(String[] subArgs) {
		myArgs = subArgs;
		myOptions = new Options(subArgs, "Status gives general information about crash events");
		myOptions.addMainOption("--uptime", "-u", "", false, Multiplicity.ZERO_OR_ONE, "Gives the uptime of the phone sonce last swupdate");
	}

	@Override
	public boolean checkArgs() {
		return myOptions.check();
	}

	private void displayDbstatus() throws IOException{
		DBManager aDB = new DBManager();
		if (!aDB.isOpened()){
			throw new IOException("Database not opened!");
		}

		System.out.println("Version database : "  + aDB.getVersion());
		System.out.println("Number of critical crash : "  +aDB.getNumberEventByCriticty(true));
		System.out.println("Number of events : "  +aDB.getNumberEventByCriticty(false));
		aDB.close();
	}
}
