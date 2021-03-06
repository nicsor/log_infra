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

package com.intel.parsing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import com.intel.crashreport.core.ParsableEvent;

import android.os.SystemProperties;

public class MainParser{

	public static final String PATH_LOGS = SystemProperties.get("persist.vendor.crashlogd.root", "/logs");
	public static final String PATH_UUID = PATH_LOGS + "/uuid.txt";

	private final static String[] LEGACY_BOARD_FABRIC = {"redhookbay","victoriabay"};
	private final static String[] FABRIC_TAGS = { "FABRICERR", "MEMERR",
		"INSTERR", "SRAMECCERR", "HWWDTLOGERR", "FABRIC_FAKE", "FIRMWARE",
		"NORTHFUSEERR", "KERNELWDT", "KERNEHANG", "SCUWDT", "FABRICXML", "PLLLOCKERR",
		"UNDEFL1ERR", "PUNITMBBTIMEOUT", "VOLTKERR", "VOLTSAIATKERR",
		"LPEINTERR", "PSHINTERR", "FUSEINTERR", "IPC2ERR", "KWDTIPCERR" };
	private final static ArrayList<String> criticalTypes = new ArrayList<String>(
		Arrays.asList("IPANIC", "FABRICERR", "IPANIC_SWWDT", "IPANIC_HWWDT",
		"HWWDTLOGERR", "MSHUTDOWN", "UIWDT", "WDT", "VMMTRAP", "VMM_UNHANDLED",
		"SECPANIC", "MPANIC", "SWWDT"));
	private String sOutput = null;
	private String sTag = "";
	private String sCrashID = "";
	private String sUptime = "";
	private String sBuild = "";
	private String sBoard = "";
	private String sDate = "";
	private String sImei = "";
	private Writer myOutput = null;
	private int iDataReady = 1;
	private String sOperator = "";
	private boolean bCritical = false;

	public MainParser(String aOutput, String aTag, String aCrashID, String aUptime,
			String aBuild, String aBoard, String aDate, String aImei){
		this(aOutput,aTag,aCrashID,aUptime,aBuild,aBoard,aDate,aImei,1,"");
	}

	public MainParser(ParsableEvent aEvent, String aBoard, String aDate, String aOperator){
		this(aEvent.getCrashDir(), aEvent.getType(), aEvent.getEventId(),
				aEvent.getUptime(), aEvent.getBuildId(),aBoard, aDate,
				aEvent.getImei(),aEvent.getDataReadyAsInt(),aOperator);
	}

	public MainParser(String aOutput, String aTag, String aCrashID, String aUptime,
			String aBuild, String aBoard, String aDate, String aImei, int aData_Ready,
			String aOperator){
		sOutput = aOutput;
		sTag = aTag;
		sCrashID = aCrashID;
		sUptime = aUptime;
		sBuild = aBuild;
		sBoard = aBoard;
		sDate = aDate;
		sImei = aImei;
		iDataReady = aData_Ready;
		sOperator = aOperator;
	}

	public int execParsing(){
		String sCrashfilename= sOutput + "/crashfile";
		String sDropbox = "";


		if (sTag.equals( "LOST_DROPBOX_JAVACRASH" )) {
			sTag="JAVACRASH";
			sDropbox="full";
		}

		if (sTag.equals( "LOST_DROPBOX_ANR" )) {
			sTag="ANR";
			sDropbox="full";
		}


		if (sTag.equals( "LOST_DROPBOX_UIWDT")) {
			sTag="UIWDT";
			sDropbox="full";
		}

		if (sTag.equals( "LOST_DROPBOX_WTF")) {
			sTag="WTF";
			sDropbox="full";
		}

		File fOutput = new File(sOutput);

		if (fOutput.isDirectory()){
			if (prepare_crashfile( sTag, sCrashfilename,  sCrashID, sUptime, sBuild,  sBoard, sDate, sImei, sOperator)) {
				if (sDropbox.equals("full" )){
					if (!fulldropbox()){
						closeOutput();
						return -1;
					}
				}

				if (sTag.equals("IPANIC") || sTag.equals("IPANIC_SWWDT") || sTag.equals("IPANIC_HWWDT")
						|| sTag.equals("IPANIC_FAKE" )|| sTag.equals("IPANIC_SWWDT_FAKE")) {
					if (!ipanic(sOutput)){
						closeOutput();
						return -1;
					}
				}

				if (sTag.equals("JAVACRASH")){
					if (!javacrash(sOutput)){
						closeOutput();
						return -1;
					}
				}

				if  (sTag.equals("ANR" )){
					if (!anr(sOutput)){
						closeOutput();
						return -1;
					}
				}

				if  (sTag.equals("UIWDT" )){
					if (!uiwdt(sOutput)){
						closeOutput();
						return -1;
					}
				}

				if  (sTag.equals("WTF" )){
					if (!wtf(sOutput)){
						closeOutput();
						return -1;
					}
				}

				if (sTag.equals("TOMBSTONE") || sTag.equals("JAVA_TOMBSTONE")) {
					if (!tombstone(sOutput)){
						closeOutput();
						return -1;
					}
				}

				if (isFabricTag(sTag)) {
					boolean bUseNewFabric = true;
					for (String sBoardNew : LEGACY_BOARD_FABRIC){
						if (sBoardNew.equals(sBoard)){
							bUseNewFabric = false;
							break;
						}
					}
					if (bUseNewFabric){
						if (!newFabricerr(sOutput, sTag)){
							closeOutput();
							return -1;
						}
					}else{
						if (!fabricerr(sOutput)){
							closeOutput();
							return -1;
						}
					}
				}

				if (sTag.equals("MPANIC") || sTag.equals("MPANIC_FAKE")) {
					if (!mpanic(sOutput)){
						closeOutput();
						return -1;
					}
				}

				if (sTag.equals("APIMR") || sTag.equals("APCOREDUMP")) {
					if (!genericCrash(sOutput)){
						closeOutput();
						return -1;
					}
				}
				//add generic parsing for unknown tag?

				if (sTag.equals("VMMTRAP")) {
					if (!vmmtrap(sOutput)){
						closeOutput();
						return -1;
					}
				}

				finish_crashfile(sOutput);
				//Close the output only at the end of the process
				closeOutput();
			}else{
				return -1;
			}
		}
		return 0;
	}

	private boolean isFabricTag(String aTag){
		boolean bResult = false;
		for (String sAllowedTag : FABRIC_TAGS){
			if (sAllowedTag.equals(aTag)){
				bResult = true;
				break;
			}
		}
		return bResult;
	}


	private boolean prepare_crashfile(String aTag, String aCrashfilename, String aCrashid, String aUptime,
			String aBuild, String aBoard, String aDate, String aImei, String sOperator) {
		boolean bResult = true;
		BufferedReaderClean uuid = null;
		try{
			uuid = new BufferedReaderClean(new FileReader(PATH_UUID));
			String sUuid = uuid.readLine();
			//Output object creation mandatory here
			myOutput = new BufferedWriter(new FileWriter(aCrashfilename));

			// TO DO LATER : manage other EVENTNAME than crash
			// with this mechanism only crash event is supported
			bResult &= appendToCrashfile( "EVENT=CRASH");
			bResult &= appendToCrashfile( "ID=" + aCrashid);
			bResult &= appendToCrashfile( "SN=" + sUuid);
			bResult &= appendToCrashfile( "DATE=" + aDate);
			bResult &= appendToCrashfile( "UPTIME=" + aUptime);
			bResult &= appendToCrashfile( "BUILD=" + aBuild);
			bResult &= appendToCrashfile( "BOARD=" + aBoard);
			bResult &= appendToCrashfile( "IMEI=" + aImei);
			bResult &= appendToCrashfile( "TYPE=" + aTag);
			bResult &= appendToCrashfile( "DATA_READY=" + iDataReady);
			bResult &= appendToCrashfile( "OPERATOR=" + sOperator);

		} catch (Exception e) {
			APLog.e( "prepare_crashfile : " + e);
			e.printStackTrace();
			return false;
		} finally {
			if (uuid != null) {
				uuid.close();
			}
		}
		return bResult;
	}

	private void closeOutput(){
		if (myOutput != null){
			try{
				myOutput.close();
			}catch (Exception e) {
				APLog.e("error on crashfile close");
				e.printStackTrace();
			}
		}
	}

	private boolean fulldropbox(){
		return appendToCrashfile("DATA0=full dropbox");
	}

	private boolean finish_crashfile(String aFolder){
		boolean bResult = true;

		if (!bCritical)
			bCritical = criticalTypes.contains(sTag.trim());
		bResult &= appendToCrashfile("CRITICAL=" + (bCritical ? "YES" : "NO"));
		//needed to identify legacy_parsing with ParserDirector
		bResult &= appendToCrashfile("PARSER=LEGACY_PARSER");
		bResult &= appendToCrashfile("_END");
		Pattern patternSD = java.util.regex.Pattern.compile(".*mnt.*sdcard.*");
		Matcher matcherFile = patternSD.matcher(aFolder);
		if (!matcherFile.find()){
			try {
				Runtime rt = Runtime.getRuntime ();
				rt.exec("chown system.log " + aFolder);
			}catch (Exception e){
				APLog.e( "chown system.log failed : " + e);
				e.printStackTrace();
			}
		}
		return bResult;
	}

	private boolean mpanic(String aFolder){
		boolean bResult;

		//a specific parsing is expected for MPANIC case
		bResult = checkCoredumpMpanic(aFolder);
		return bResult;
	}

	private boolean vmmtrap(String aFolder){
		boolean bResult;

		//use basic coredump parsing method for vmmtrap
		bResult = checkCoredump(aFolder);
		return bResult;
	}


	private boolean checkCoredump(String aFolder){
		boolean bResult = true;
		boolean bData0Found = false;
		boolean bData1Found = false;
		boolean bData2Found = false;
		String sData0="";
		String sData1="";
		String sData2="";

		String sCoreDumpFile = fileGrepSearch("coredump_.*.txt", aFolder);
		if (!sCoreDumpFile.isEmpty()) {
			BufferedReaderClean bufCoreFile = null;
			try{
				Pattern patternData0 = java.util.regex.Pattern.compile("Filename:.*");
				Pattern patternData1 = java.util.regex.Pattern.compile("Line number:.*");
				Pattern patternData2 = java.util.regex.Pattern.compile("Log data:.*");
				bufCoreFile = new BufferedReaderClean(new FileReader(sCoreDumpFile));
				String sCurLine;
				while ((sCurLine = bufCoreFile.readLine()) != null) {
					String sTmp;
					if (!bData0Found){
						sTmp = simpleGrepAwk(patternData0, sCurLine, ":", 1, true);
						if (sTmp != null){
							sData0 = sTmp;
							bData0Found = true;
						}
					}
					if (!bData1Found){
						sTmp = simpleGrepAwk(patternData1, sCurLine, ":", 1, true);
						if (sTmp != null){
							sData1 = sTmp;
							bData1Found = true;
						}
					}
					if (!bData2Found){
						sTmp = simpleGrepAwk(patternData2, sCurLine, ":", 1, true);
						if (sTmp != null){
							sData2 = sTmp;
							bData2Found = true;
						}
					}
				}
				bResult &= appendToCrashfile("DATA0=" + sData0);
				bResult &= appendToCrashfile("DATA1=" + sData1);
				bResult &= appendToCrashfile("DATA2=" + sData2);
			}
			catch(Exception e) {
				APLog.e( "checkCoredump : " + e);
				e.printStackTrace();
				return false;
			} finally {
				if (bufCoreFile != null) {
					bufCoreFile.close();
				}
			}
		}else{
			//using default parsing method
			return genericCrash(aFolder);
		}
		return bResult;
	}

	private boolean checkCoredumpMpanic(String aFolder){
		boolean bResult = true;
		boolean bFileNameFound = false;
		boolean bLineFound = false;
		boolean bVectorFound = false;
		String sFileName="";
		String sLine="";
		String sVector="";
		BufferedReaderClean bufCoreFile = null;
		String sCoreDumpFile;

		String sCoreDumpGZ = fileGrepSearch("coredump_.*txt\\.gz", aFolder);
		FileInputStream f = null;
		try {
			// 1st try on zip pattern then normal
			if (!sCoreDumpGZ.isEmpty()){
				f = new FileInputStream(sCoreDumpGZ);
				GZIPInputStream gzipInputStream = new GZIPInputStream(f);
				bufCoreFile = new BufferedReaderClean(new InputStreamReader(gzipInputStream));
			}else{
				sCoreDumpFile = fileGrepSearch("coredump_.*txt" , aFolder);
				if (!sCoreDumpFile.isEmpty()){
					bufCoreFile = new BufferedReaderClean(new FileReader(sCoreDumpFile));
				}
			}
		} catch(Exception e) {
			silentClose(f);
		}

		if (bufCoreFile != null) {
			try{
				Pattern patternFileName = java.util.regex.Pattern.compile("Filename:.*");
				Pattern patternLine = java.util.regex.Pattern.compile("Line number:.*");
				Pattern patternVector = java.util.regex.Pattern.compile("Vector:.*");
				String sCurLine;
				while ((sCurLine = bufCoreFile.readLine()) != null) {
					String sTmp;
					if (!bFileNameFound){
						sTmp = simpleGrepAwk(patternFileName, sCurLine, ":", 1, true);
						if (sTmp != null){
							sFileName = sTmp;
							bFileNameFound = true;
						}
					}
					if (!bLineFound){
						sTmp = simpleGrepAwk(patternLine, sCurLine, ":", 1, true);
						if (sTmp != null){
							sLine = sTmp;
							bLineFound = true;
						}
					}
					if (!bVectorFound){
						sTmp = simpleGrepAwk(patternVector, sCurLine, ":", 1, true);
						if (sTmp != null){
							sVector = sTmp;
							bVectorFound = true;
						}
					}
				}
				//need to trim space per crashtool request
				bResult &= appendToCrashfile("DATA0=" + "id: l:" + sLine.trim() + " c:" + sFileName.trim());
				bResult &= appendToCrashfile("DATA1=" + sVector);
				//hardcoded data for crashtool identification
				bResult &= appendToCrashfile("DATA3=CD_INFO");
			}
			catch(Exception e) {
				APLog.e( "checkCoredumpMpanic : " + e);
				e.printStackTrace();
				return false;
			} finally {
				if (bufCoreFile != null) {
					bufCoreFile.close();
					silentClose(f);
				}
			}
		}else{
			//using default parsing method
			return genericCrash(aFolder);
		}
		return bResult;
	}

	private boolean genericCrash(String aFolder){
		boolean bResult = true;
		boolean bData0Found = false;
		boolean bData1Found = false;
		boolean bData2Found = false;
		boolean bData3Found = false;
		boolean bData4Found = false;
		boolean bData5Found = false;
		boolean bDataModemFound = false;

		String sData0="";
		String sData1="";
		String sData2="";
		String sData3="";
		String sData4="";
		String sData5="";
		String sModemversionUsed="";
		String sGenFile = fileGrepSearch(".*_crashdata", aFolder);
		if (!sGenFile.isEmpty()){
			BufferedReaderClean bufGenFile = null;
			try{
				Pattern patternData0 = java.util.regex.Pattern.compile("DATA0=.*");
				Pattern patternData1 = java.util.regex.Pattern.compile("DATA1=.*");
				Pattern patternData2 = java.util.regex.Pattern.compile("DATA2=.*");
				Pattern patternData3 = java.util.regex.Pattern.compile("DATA3=.*");
				Pattern patternData4 = java.util.regex.Pattern.compile("DATA4=.*");
				Pattern patternData5 = java.util.regex.Pattern.compile("DATA5=.*");
				Pattern patternModemUsed = java.util.regex.Pattern.compile("MODEMVERSIONUSED=.*");
				bufGenFile = new BufferedReaderClean(new FileReader(sGenFile));
				String sCurLine;
				while ((sCurLine = bufGenFile.readLine()) != null) {
					String sTmp;
					if (!bData0Found){
						sTmp = simpleGrepAwk(patternData0, sCurLine, "=", 1, true);
						if (sTmp != null){
							sData0 = sTmp;
							bData0Found = true;
						}
					}
					if (!bData1Found){
						sTmp = simpleGrepAwk(patternData1, sCurLine, "=", 1, true);
						if (sTmp != null){
							sData1 = sTmp;
							bData1Found = true;
						}
					}
					if (!bData2Found){
						sTmp = simpleGrepAwk(patternData2, sCurLine, "=", 1, true);
						if (sTmp != null){
							sData2 = sTmp;
							bData2Found = true;
						}
					}
					if (!bData3Found){
						sTmp = simpleGrepAwk(patternData3, sCurLine, "=", 1, true);
						if (sTmp != null){
							sData3 = sTmp;
							bData3Found = true;
						}
					}
					if (!bData4Found){
						sTmp = simpleGrepAwk(patternData4, sCurLine, "=", 1, true);
						if (sTmp != null){
							sData4 = sTmp;
							bData4Found = true;
						}
					}
					if (!bData5Found){
						sTmp = simpleGrepAwk(patternData5, sCurLine, "=", 1, true);
						if (sTmp != null){
							sData5 = sTmp;
							bData5Found = true;
						}
					}
					if (!bDataModemFound){
						sTmp = simpleGrepAwk(patternModemUsed, sCurLine, "=", 1, true);
						if (sTmp != null){
							sModemversionUsed = sTmp;
							bDataModemFound = true;
						}
					}
				}
				bResult &= appendToCrashfile("DATA0=" + sData0);
				bResult &= appendToCrashfile("DATA1=" + sData1);
				bResult &= appendToCrashfile("DATA2=" + sData2);
				bResult &= appendToCrashfile("DATA3=" + sData3);
				bResult &= appendToCrashfile("DATA4=" + sData4);
				bResult &= appendToCrashfile("DATA5=" + sData5);
				bResult &= appendToCrashfile("MODEMVERSIONUSED=" + sModemversionUsed);
			}
			catch(Exception e) {
				APLog.e( "modemcrash : " + e);
				e.printStackTrace();
				return false;
			} finally {
				if (bufGenFile != null) {
					bufGenFile.close();
				}
			}
		}
		return bResult;
	}




	private Map<String,String> ipanicByFile(String aFile){

		String sData0;
		Map<String,String> computedData = new HashMap<String,String>();

		String sDataDefault="";
		StringBuffer sDataLockUp = new StringBuffer();
		String sComm = "";
		String sPanic= "";
		boolean bDataFound = false;
		boolean bDataRipFound = false;
		boolean bCommFound = false;
		boolean bCandidateCommFound = false;
		boolean bPanicFound = false;
		boolean bNmiFound = false;
		boolean bLockUpCase = false;
		int iCallTraceCount = 0;

		BufferedReaderClean bufPanicFile = null;
		try{
			bufPanicFile = new BufferedReaderClean(new FileReader(aFile));
			Pattern patternData = java.util.regex.Pattern.compile("EIP:.*SS:ESP");
			Pattern patternData_64 = java.util.regex.Pattern.compile("RIP  \\[.*ffffffff.*\\].*");
			Pattern patternComm = java.util.regex.Pattern.compile("(c|C)omm: .*");
			Pattern patternPanic = java.util.regex.Pattern.compile("Kernel panic - not syncing: .*");
			Pattern patternHardLock = java.util.regex.Pattern.compile("hard LOCKUP.*");
			Pattern patternNmiEnd = java.util.regex.Pattern.compile("nmi_stack_correct.*");

			String sCurLine;
			while ((sCurLine = bufPanicFile.readLine()) != null) {
				String sTmp;
				if (!bDataFound){
					sTmp = simpleGrepAwk(patternData, sCurLine, " ", 2);
					if (sTmp==null){
						//second chance with 64 pattern
						sTmp = simpleGrepAwk(patternData_64, sCurLine, " ", 3);
						if (sTmp==null){
							if (bDataRipFound){
								sTmp = simpleAwk( sCurLine, ">]", 1);
								//third chance check on next "rip" line
							}
						}
					}
					bDataRipFound = false;
					if (sTmp != null){
						sDataDefault = sTmp;
						bDataFound = true;
						if (bCandidateCommFound){
							//we use last "comm" found
							bCommFound = true;
						}
					}
					//pre-requisite for the 3rd chance
					if (sCurLine.contains("RIP")){
						bDataRipFound = true;
					}
				}


				if (!bCommFound){
					sTmp = simpleGrepAwk(patternComm, sCurLine, " ", 1);
					if (sTmp != null){
						sComm = sTmp;
						//considered found when data "SS:EP" is found
						//Panicfound is also a condition to store "comm"
						//if it is not found, we keep value but continue seeking pattern
						if (bDataFound || bPanicFound){
							bCommFound = true;
						}else{
							bCandidateCommFound = true;
						}
					}
				}

				if (!bPanicFound){
					sTmp = simpleGrepAwk(patternPanic, sCurLine, ":", 1);
					if (sTmp != null){
						sPanic = sTmp;
						bPanicFound = true;
						sTmp = simpleGrepAwk(patternHardLock, sCurLine, "", 0);
						if (sTmp != null)
							bLockUpCase = true;
					}
				}

				if (bLockUpCase){
					if(!bNmiFound) {
						sTmp = simpleGrepAwk(patternNmiEnd, sCurLine, "", 0);
						if (sTmp != null){
							bNmiFound = true;
						}
					} else if (iCallTraceCount < 4){
						//get line value with a stack trace filter
						String sCallLine = formatStackTrace(sCurLine);
						if (!sCallLine.isEmpty()){
							sDataLockUp.append(sCallLine);
							iCallTraceCount++;
						}
					}
				}
			}

			//filter step
			// 1 - remove number after "/" pattern
			if (sComm.contains("/")){
				String sFilteredValue = simpleAwk(sComm, "/",0);
				if (sFilteredValue != null && !sFilteredValue.isEmpty()){
					sComm =  sFilteredValue;
				}
			}
			// 2 - remove thread name if "Fatal exception in interrupt"
			if (sPanic.contains("Fatal exception in interrupt")){
				sComm = "";
			}
			// 3 - remove CPU number in "Watchdog detected hard LOCKUP on cpu N"
			if (bLockUpCase){
				String sFilteredValue = simpleAwk(sPanic, " on cpu",0);
				if (sFilteredValue != null && !sFilteredValue.isEmpty()){
					sPanic =  sFilteredValue;
				}
			}


			if (bLockUpCase){
				sData0 = sDataLockUp.toString();
			} else {
				sData0 = getStackForPanic(sPanic,aFile);
			}

			if (sData0.isEmpty()){
				//use default data
				sData0 = sDataDefault;
			}
			computedData.put("DATA0", sData0);
			computedData.put("DATA1", sComm);
			computedData.put("DATA2", sPanic);
			if (aFile.contains("emmc_ipanic_console")){
				computedData.put("DATA3", "emmc");
			} else {
				if (aFile.contains("ram_ipanic_console")){
					computedData.put("DATA3", "ram");
				}
			}
		}
		catch(Exception e) {
			APLog.e( "iPanicByFile : " + e);
			e.printStackTrace();
			return computedData;
		} finally {
			if (bufPanicFile != null){
				bufPanicFile.close();
			}
		}
		return computedData;
	}

	private boolean checkSignature(Map<String,String> aData){
		String sTmp;
		boolean bData0Found = false;
		boolean bData2Found = false;

		if (aData == null){
			return false;
		}
		sTmp = aData.get("DATA0");
		if (sTmp != null){
			if (!sTmp.isEmpty()){
				bData0Found = true;
			}
		}

		//DATA1 is not relevant - so no check

		sTmp = aData.get("DATA2");
		if (sTmp != null){
			if (!sTmp.isEmpty()){
				bData2Found = true;
			}
		}

		//signature is considered valid when one of DATA0 or DATA2 is not empty
		return bData0Found || bData2Found;
	}


	private boolean ipanic(String aFolder){
		boolean bResult = true;
		boolean bFoundOnce = false;
		Map<String,String> resultData = null;

		// test panic_console first because it is the smallest file
		String sIPanicFile = fileGrepSearch(".*panic_console.*", aFolder);
		if (!sIPanicFile.isEmpty()){
			bFoundOnce = true;
			resultData = ipanicByFile(sIPanicFile);
		}

		if (!checkSignature(resultData)){
			resultData = null;
			//2nd chance : use coredump.txt file
			sIPanicFile = fileGrepSearch("coredump.*", aFolder);
			if (!sIPanicFile.isEmpty()){
				bFoundOnce = true;
				resultData = ipanicByFile(sIPanicFile);
			}
		}

		if (!checkSignature(resultData)){
			resultData = null;
			//3rd chance : use console-ramoops pattern
			sIPanicFile = fileGrepSearch(".*console-ramoops.*", aFolder);
			if (!sIPanicFile.isEmpty()){
				bFoundOnce = true;
				resultData = ipanicByFile(sIPanicFile);
			}
		}
		if (!checkSignature(resultData)){
			resultData = null;
			//4th chance : use last_kmsg pattern
			sIPanicFile = fileGrepSearch(".*last_kmsg.*", aFolder);
			if (!sIPanicFile.isEmpty()){
				bFoundOnce = true;
				resultData = ipanicByFile(sIPanicFile);
			}
		}

		if (!checkSignature(resultData)){
			resultData = null;
		}

		if (resultData != null){
			String sData0 = "";
			String sData1 = "";
			String sData2 = "";
			String sData3 = "";
			String sTmp;

			sTmp = resultData.get("DATA0");
			if (sTmp != null){
				sData0 = sTmp;
			}
			sTmp = resultData.get("DATA1");
			if (sTmp != null){
				sData1 = sTmp;
			}
			sTmp = resultData.get("DATA2");
			if (sTmp != null){
				sData2 = sTmp;
			}
			sTmp = resultData.get("DATA3");
			if (sTmp != null){
				sData3 = sTmp;
			}

			bResult &= appendToCrashfile("DATA0=" + sData0);
			bResult &= appendToCrashfile("DATA1=" + sData1);
			bResult &= appendToCrashfile("DATA2=" + sData2);
			bResult &= appendToCrashfile("DATA3=" + sData3);

		} else {
			bResult &= appendToCrashfile("DATA0=insufficient data");
		}

		if (!bFoundOnce){
			//4th chance, try header
			String sHeaderFile = fileGrepSearch(".*ipanic_header.*", aFolder);
			if (!sHeaderFile.isEmpty()){
				bResult &= appendToCrashfile("DATA3=emmc_hdr");
			}
		}
		return bResult;
	}

	private String getStackForPanic(String sPanicValue, String sPathToParse){
		//get stack for only specific panic case
		if(sPanicValue.contains("softlockup")){
			// for IPI deadlockcase, we use a special pattern
			String sIpi = extractStackTrace(".*waiting for CSD lock.*", sPathToParse);
			if (sIpi.isEmpty()) {
				return extractStackTrace("soft lockup", sPathToParse);
			} else {
				return sIpi;
			}
		}
		return "";
	}

	private String extractStackTrace(String sBugProcess, String sPathToParse){
		StringBuffer sResult = new StringBuffer();
		boolean bBugFound = false;
		boolean bDataFound = false;
		boolean bCallTraceFound = false;
		int bCallTraceCount = 0;

		BufferedReaderClean aBuf = null;
		try{
			aBuf = new BufferedReaderClean(new FileReader(sPathToParse));
			Pattern patternBug = java.util.regex.Pattern.compile("BUG: " + sBugProcess);
			Pattern patternData = java.util.regex.Pattern.compile("EIP:.*");
			Pattern patternData_64 = java.util.regex.Pattern.compile("RIP: .*\\[.*ffffffff.*\\].*");
			String sCurLine;
			while ((sCurLine = aBuf.readLine()) != null) {
				String sTmp;

				if (!bBugFound) {
					sTmp = simpleGrepAwk(patternBug, sCurLine, "", 0);
					if (sTmp != null) {
						bBugFound = true;
					}
				} else {
					//we are inside the search process, extract call trace
					if (!bDataFound) {
						sTmp = simpleGrepAwk(patternData, sCurLine, " ", 2);
						if (sTmp==null) {
							//second chance with 64 pattern
							sTmp = simpleGrepAwk(patternData_64, sCurLine, ">]", 2);
						}
						if (sTmp != null) {
							sResult.append(sTmp + " - ");
							bDataFound = true;
						}
					}
					if (sCurLine.contains("Call Trace:")) {
						bCallTraceFound = true;
						continue;
					}

					if (bCallTraceFound) {
						sResult.append(" " + formatStackTrace(sCurLine));
						bCallTraceCount++;
					}
					if (bCallTraceCount >= 8){
						//extract finish
						break;
					}
				}
			}
		} catch(Exception e) {
			System.err.println( "extractStackTrace : " + e);
			e.printStackTrace();
			return "";

		} finally {
			if (aBuf != null) {
				aBuf.close();
			}
		}
		return sResult.toString();
	}

	private String formatStackTrace(String sLine){
		String sResult = "";
		int iEndTimeStamp = sLine.indexOf(">] ");
		if (iEndTimeStamp > 0){
			sResult = sLine.substring(iEndTimeStamp + 2);
			//filtering ghost pattern
			int iIndHost = sResult.indexOf(" ? ");
			if (iIndHost >= 0){
				//entire line should be filtered
				sResult = "";
			}
		}
		return sResult;
	}

	private boolean fabricerr(String aFolder){
		boolean bResult = true;

		String sFabricFile = fileGrepSearch(".*ipanic_fabric_err.*", aFolder);
		if (!sFabricFile.isEmpty()){
			String sData0 = "";
			String sData1 = "";
			StringBuffer sData2 = new StringBuffer();
			boolean bData0Found = false;
			boolean bData1Found = false;
			boolean bData2Found = false;
			boolean bForcedFabric = false;
			ArrayList<String> ldata1_2 = new ArrayList<String>();

			BufferedReaderClean bufFabricFile = null;
			try{
				bufFabricFile = new BufferedReaderClean(new FileReader(sFabricFile));
				Pattern patternForcedFabric = java.util.regex.Pattern.compile(".*HW WDT expired.*");
				//suspicious regex repeating r has no effect
				//   data0=`grep "DW0:" $1/ipanic_fabric_err*`
				//   data1=`grep "DW1:" $1/ipanic_fabric_err*`
				//   data2=`grep "DW11:" $1/ipanic_fabric_err*`
				Pattern patternData0 = java.util.regex.Pattern.compile(".*DW0:.*");
				Pattern patternData1 = java.util.regex.Pattern.compile(".*DW1:.*");
				Pattern patternData2 = java.util.regex.Pattern.compile(".*DW11:.*");
				Pattern patternData0_1_2 = java.util.regex.Pattern.compile(".*[erroir|:].*");
				Pattern patternInvertData0_1_2 = java.util.regex.Pattern.compile(".*(Fabric Error|summary|Additional|Decoded).*");

				String sCurLine;
				//First loop for checking force_fabric
				while ((sCurLine = bufFabricFile.readLine()) != null) {
					String sTmp;
					sTmp = simpleGrepAwk(patternForcedFabric, sCurLine, "", 0);
					if (sTmp != null){
						bForcedFabric = true;
						break;
					}
				}
				bufFabricFile.close();
				//No proper reinit method, need to recreate stream
				bufFabricFile = new BufferedReaderClean(new FileReader(sFabricFile));
				while ((sCurLine = bufFabricFile.readLine()) != null) {
					//data0=`grep "[erroir|:]" $1/ipanic_fabric_err* | grep -v -E 'summary|Additional|Decoded' | grep -m1 ".*" | awk -F"[" '{print $1}'`
					String sTmp;
					if (bForcedFabric){
						if (!bData0Found){
							sTmp = simpleGrepAwk(patternData0, sCurLine, "", 0);
							if (sTmp != null){
								sData0 = sTmp;
								bData0Found = true;
							}
						}
						if (!bData1Found){
							sTmp = simpleGrepAwk(patternData1, sCurLine, "", 0);
							if (sTmp != null){
								sData1 = sTmp;
								bData1Found = true;
							}
						}
						if (!bData2Found){
							sTmp = simpleGrepAwk(patternData2, sCurLine, "", 0);
							if (sTmp != null){
								sData2.append(sTmp);
								bData2Found = true;
							}
						}
					}else{
						if (checkGrepInvertGrep(patternData0_1_2,patternInvertData0_1_2,sCurLine) )	{
							ldata1_2.add(sCurLine);
							if (!bData0Found){
								sTmp = simpleAwk(sCurLine,"\\[", 0);
								if (sTmp != null){
									sData0 = sTmp;
									bData0Found = true;
								}
							}
						}
					}
				}
				if (!bForcedFabric){
					//	data1=`grep "[erroir|:]" $1/ipanic_fabric_err* | grep -v -E 'summary|Additional|Decoded' | grep -m2 ".*" | tail -1 | awk -F"(" '{print $1}'`
					if (ldata1_2.size()>= 2){
						sData1 = ldata1_2.get(1);
					}else if (ldata1_2.size() == 1){
						sData1 = ldata1_2.get(0);
					}
					sData1 = simpleAwk(sData1,"\\(", 0);
					//	data2=`grep "[erroir|:]" $1/ipanic_fabric_err* | grep -v -E 'summary|Additional|Decoded' | tail -n +3 | grep -m4 ".*" | sed  -e "N;s/\n/ /" | sed -e "N;s/\n/ /"`
					int iDebData2 = 2;
					int iendData2 = java.lang.Math.min(ldata1_2.size(),iDebData2+4);
					for (int i = iDebData2; i < iendData2; i++) {
						if (i == iDebData2){
							sData2.delete(0, sData2.length());
							sData2.append(ldata1_2.get(i));
						}else {
							sData2.append(" " + ldata1_2.get(i));
						}
					}
				}
				bResult &= appendToCrashfile("DATA0=" + sData0);
				bResult &= appendToCrashfile("DATA1=" + sData1);
				bResult &= appendToCrashfile("DATA2=" + sData2.toString());
			}
			catch(Exception e) {
				APLog.e( "fabricerr : " + e);
				e.printStackTrace();
				return false;
			} finally {
				if (bufFabricFile != null) {
					bufFabricFile.close();
				}
			}
		}
		return bResult;
	}

	private boolean isDataLine(String sTestString){
		//ignore "* " line
		if (sTestString.startsWith("* ")){
			return false;
		}
		//ignore "---------" line
		if (sTestString.startsWith("---------")){
			return false;
		}
		//ignore blank line
		if (sTestString.trim().isEmpty()){
			return false;
		}
		return true;
	}

	private boolean newFabricerr(String aFolder, String aTag){
		boolean bResult = true;

		String sFabricFile = fileGrepSearch(".*ipanic_fabric_err.*", aFolder);
		if (!sFabricFile.isEmpty()){
			StringBuffer sData0 = new StringBuffer();
			StringBuffer sData1 = new StringBuffer();
			StringBuffer sData2 = new StringBuffer();
			StringBuffer sDataHole = new StringBuffer();
			String sData4 = "";
			boolean bData0_1Found = false;
			boolean bPatternStart0_1Found = false;
			boolean bData2Found = false;
			int iSubData0Count=0;
			int iSubData1Count=0;
			boolean bSubData2Found = false;
			int iSubData2Count=0;
			boolean bDataHoleFound = false;
			boolean bSubDataHoleFound = false;
			int iSubDataHoleCount=0;
			boolean bData4Found = false;
			boolean bSCUWDT = false;
			StringBuffer sDataSCUWDT = new StringBuffer();

			//specific code for SCUWDT to be reworked inside a crashtool parser
			if (aTag.equals("SCUWDT")){
				bSCUWDT = true;
			}

			BufferedReaderClean bufFabricFile = null;
			try{
				Pattern patternData0_1 = java.util.regex.Pattern.compile("Summary of Fabric Error detail:");
				Pattern patternData2 = java.util.regex.Pattern.compile(".*ERROR LOG.*");
				Pattern patternHole = java.util.regex.Pattern.compile(".*Address Hole.*");
				Pattern patternData4 = java.util.regex.Pattern.compile(".*Length of fabric error file:.*");
				String sCurLine;

				bufFabricFile = new BufferedReaderClean(new FileReader(sFabricFile));
				while ((sCurLine = bufFabricFile.readLine()) != null) {

					if (!bData0_1Found){
						//check sub data first
						if (bPatternStart0_1Found){
							//search should stop if pattern Data2 has been found
							if (bSubData2Found){
								bData0_1Found = true;
							}else{
								//need to check that line is eligible for data content
								if (isDataLine(sCurLine)){
									//just concat the 2 following lines
									if (iSubData0Count < 2){
										if (sData0.length() != 0){
											sData0.append(" / ");
										}
										sData0.append(sCurLine);
										iSubData0Count++;
									}else if (iSubData1Count < 2){
										if (sData1.length() != 0){
											sData1.append(" / ");
										}
										sData1.append(sCurLine);
										iSubData1Count++;
									}
									if (iSubData1Count >= 2){
										bData0_1Found = true;
									}
								}
							}
						}else{
							String sTmpData0;
							sTmpData0 = simpleGrepAwk(patternData0_1, sCurLine, "", 0);
							if (sTmpData0 != null){
								iSubData0Count = 0;
								iSubData1Count = 0;
								bPatternStart0_1Found = true;
								sData0.delete(0, sData0.length());
								sData1.delete(0, sData1.length());
							}
						}
					}

					if (!bDataHoleFound){
						//check sub data first
						if (bSubDataHoleFound){
							if (isDataLine(sCurLine)){
								//just concat 4 following lines
								if (iSubDataHoleCount < 4){
									if (sDataHole.length() != 0){
										sDataHole.append(" / ");
									}else{
										sDataHole.append("Address hole : ");
									}
									sDataHole.append(sCurLine);
									iSubDataHoleCount++;
								}else{
									bDataHoleFound = true;
								}
							}
						}else{
							String sTmpDataHole;
							sTmpDataHole = simpleGrepAwk(patternHole, sCurLine, "", 0);
							if (sTmpDataHole != null){
								iSubDataHoleCount = 0;
								bSubDataHoleFound = true;
								sDataHole.delete(0, sDataHole.length());
							}
						}
					}

					if (!bData2Found){
						//check sub data first
						if (bSubData2Found){
							if (isDataLine(sCurLine)){
								//just concat 4 following lines
								if (iSubData2Count < 4){
									if (sData2.length() != 0){
										sData2.append(" / ");
									}
									sData2.append(sCurLine);
									iSubData2Count++;
								}else{
									bData2Found = true;
								}
							}
						}else{
							String sTmpData2;
							sTmpData2 = simpleGrepAwk(patternData2, sCurLine, "", 0);
							if (sTmpData2 != null){
								iSubData2Count = 0;
								bSubData2Found = true;
								sData2.delete(0, sData2.length());
							}
						}
					}
					//scuwdt specificpart
					if (bSCUWDT){
						if (sCurLine.startsWith("DW4:") || sCurLine.startsWith("DW19:")){
							sDataSCUWDT.append(sCurLine + " / ");
						}
					}
					//data4
					if (!bData4Found){
						String sTmp;
						sTmp = simpleGrepAwk(patternData4, sCurLine, ":", 1);
						if (sTmp != null){
							sData4 = sTmp;
							bData4Found = true;
						}
					}
				}

				if (bSCUWDT){
					sData2 = sDataSCUWDT;
				}
				else if (bDataHoleFound) {
					//if present, dataHole should replace data2
					sData2 = sDataHole;
				}

				bResult &= appendToCrashfile("DATA0=" + sData0);
				bResult &= appendToCrashfile("DATA1=" + sData1);
				bResult &= appendToCrashfile("DATA2=" + sData2);
				bResult &= appendToCrashfile("DATA4=" + sData4);
			}
			catch(Exception e) {
				APLog.e( "newfabricerr : " + e);
				e.printStackTrace();
				return false;
			} finally {
				if (bufFabricFile != null) {
					bufFabricFile.close();
				}
			}
		}
		return bResult;
	}

	private boolean tombstone(String aFolder){
		boolean bResult = true;
		String sTombstoneFile = fileGrepSearch("tombstone_.*", aFolder);
		//second chance : try native_crash pattern
		if (sTombstoneFile.isEmpty()) {
			sTombstoneFile = fileGrepSearch(".*native_crash.*", aFolder);
		}
		if (!sTombstoneFile.isEmpty()){
			String sProcess= "";
			String sSignal= "";
			StringBuffer sStackSymbol = new StringBuffer();
			StringBuffer sStackLibs = new StringBuffer();
			String sFaultAddress = "";
			String sFaultAddrSeparator = "fault addr ";
			String sHexCharactersPattern = "(0x)?[0-9A-Fa-f]+";
			String sSubMessage = "";
			boolean bProcessFound = false;
			boolean bSignalFound = false;
			boolean bSubSignalFound = false;
			boolean bSubMessageFound = false;
			boolean bDisplaySymbols = false;
			int iSubSignalCount = 0;
			int iStackCount = 0;
			boolean bSubStackFound = false;
			int iSubStackCount = 0;

			/*Defines patterns expected to be found in the tombstone file to extract relevant crash data*/
			Pattern patternProcess = java.util.regex.Pattern.compile(".*>>>.*");
			Pattern patternSignalStack = java.util.regex.Pattern.compile(".*Build fingerprint.*");
			Pattern patternSubSignal = java.util.regex.Pattern.compile(".*signal.*");
			Pattern patternSubStack = java.util.regex.Pattern.compile(".*#0[0-7].*");
			Pattern patternSubMessage = java.util.regex.Pattern.compile("^Abort message:.*");
			String sCurLine;
			BufferedReaderClean bufTombstoneFile = null;
			try {
				int offset = sTombstoneFile.lastIndexOf(".");
				if (offset > 0 && "gz".equals(sTombstoneFile.substring(offset + 1))) {
					FileInputStream f = new FileInputStream(sTombstoneFile);
					GZIPInputStream gzipInputStream = new GZIPInputStream(f);
					bufTombstoneFile = new BufferedReaderClean(new InputStreamReader (gzipInputStream));
				} else {
					bufTombstoneFile = new BufferedReaderClean(new FileReader(sTombstoneFile));
				}
				while ((sCurLine = bufTombstoneFile.readLine()) != null) {
					String sTmp;
					if (!bProcessFound){
						sTmp = simpleGrepAwk(patternProcess, sCurLine, ">>>", 1);
						sTmp = simpleAwk(sTmp,"<", 0);
						if (sTmp != null){
							sProcess = sTmp;
							bProcessFound = true;
						}
					}
					if (!bSignalFound){
						String sTmpSignal;
						sTmpSignal = simpleGrepAwk(patternSignalStack, sCurLine, "", 0);
						if (sTmpSignal != null){
							iSubSignalCount = 0;
							bSubSignalFound = true;
						}
						if (bSubSignalFound){
							//Search SubSignal and FaultAddress patterns only in the 5 lines following the line
							//containing SignalStack pattern
							if (iSubSignalCount < 5){
								sTmp = simpleGrepAwk(patternSubSignal, sCurLine, "\\(", 1);
								sTmp = simpleAwk(sTmp,"\\)", 0);
								if (sTmp != null){
									sSignal = sTmp;
									bSignalFound = true;
									//signal has been found : it is assumed the fault address is always in the same line
									//and will be the last entry on the line being a hex value either f1e7a375 or 0x3180
									sTmp = simpleAwk(sCurLine,sFaultAddrSeparator, 1);
									if (sTmp != null){
										if (sTmp.matches(sHexCharactersPattern)){
											sFaultAddress = sTmp;
										}
									}
								}
								iSubSignalCount++;
							}else{
								bSubSignalFound = false;
							}
						}
					}
					if (iStackCount<8){
						String sTmpStack;
						sTmpStack = simpleGrepAwk(patternSignalStack, sCurLine, "", 0);
						if (sTmpStack != null){
							iSubStackCount = 0;
							bSubStackFound = true;
						}

						if (bSubStackFound){
							if (iSubStackCount < 15){
								//required for managing line with matching subschema
								String sTmpSubStack;
								sTmpSubStack = simpleGrepAwk(patternSubStack, sCurLine,"" , 0);
								sTmp = simpleAwk(sTmpSubStack,"\\(", 1);
								sTmp = simpleAwk(sTmp,"\\)", 0);
								if (sTmp != null){
									bDisplaySymbols = true;
									if (iStackCount == 0){
										sStackSymbol.append(sTmp);
									} else{
										sStackSymbol.append(" " +  sTmp);
									}
									iStackCount++;
								}else if (sTmpSubStack != null){
									// required to reproduce exactly number of white space
									if (iStackCount != 0){
										sStackSymbol.append(" ");
									}
									sTmp = advancedAwk(sTmpSubStack," ", 3);
									if (sTmp != null){ //case without symbols
										if (iStackCount == 0){
											sStackLibs.append(sTmp);
										}else{
											sStackLibs.append(" " +  sTmp);
										}
									}else{
										//in order to also reproduce white space mechanism
										if (iStackCount != 0){
											sStackLibs.append(" ");
										}
									}
									iStackCount++;
								}
								iSubStackCount++;
							}else{
								bSubStackFound = false;
							}
						}
					}
					if (!bSubMessageFound && bSubStackFound){
						sTmp = simpleGrepAwk(patternSubMessage, sCurLine, ":", 1);
						if (sTmp != null){
							sSubMessage = sTmp + " - ";
							bSubMessageFound = true;
						}
					}
				}

				bResult &= appendToCrashfile("DATA0=" + sProcess);
				bResult &= appendToCrashfile("DATA1=" + sSignal);

				if (bDisplaySymbols){
					bResult &= appendToCrashfile("DATA2=" + sSubMessage + sStackSymbol);
				}else{
					bResult &= appendToCrashfile("DATA2=" + sSubMessage + sStackLibs);
				}
				bResult &= appendToCrashfile("DATA3=" + sFaultAddress);

				if (sTag.equals("TOMBSTONE") && "system_server".equals(sProcess.trim()))
					bCritical = true;
			}
			catch(Exception e) {
				APLog.e( "tombstone : " + e);
				e.printStackTrace();
				return false;
			} finally {
				if (bufTombstoneFile != null) {
					bufTombstoneFile.close();
				}
			}
		}
		return bResult;
	}

	private boolean uiwdt(String aFolder){
		boolean bResult = true;

		String sUIWDTFileGZ = fileGrepSearch("system_server_watchdog.*txt.gz", aFolder);
		BufferedReaderClean uiwdtReader = null;
		FileInputStream f = null;
		try {
			if (!sUIWDTFileGZ.isEmpty()){
				f = new FileInputStream(sUIWDTFileGZ);
				GZIPInputStream gzipInputStream = new GZIPInputStream(f);
				uiwdtReader = new BufferedReaderClean(new InputStreamReader (gzipInputStream));
				bResult = extractUIWDTData(uiwdtReader);
			}else{
				String sUIWDTFile = fileGrepSearch("system_server_watchdog.*txt" , aFolder);
				if (!sUIWDTFile.isEmpty()){
					uiwdtReader = new BufferedReaderClean(new FileReader(sUIWDTFile));
					bResult = extractUIWDTData(uiwdtReader);
				}
			}
		}catch(Exception e) {
			APLog.e( "UIWDT : " + e);
			e.printStackTrace();
			return false;
		} finally {
			if (uiwdtReader != null) {
				uiwdtReader.close();
			}
			silentClose(f);
		}
		return bResult;
	}

	private void silentClose(FileInputStream f){
		if (f != null) {
			try {
				f.close();
			} catch (IOException e) {
				APLog.e("IOException : " + e.getMessage());
			}
		}
	}

	private boolean wtf(String aFolder){
		boolean bResult = true;

		String sWTFFileGZ = fileGrepSearch("wtf.*.gz", aFolder);
		BufferedReaderClean wtfReader = null;
		FileInputStream f = null;
		try {
			if (!sWTFFileGZ.isEmpty()){
				f = new FileInputStream(sWTFFileGZ);
				GZIPInputStream gzipInputStream = new GZIPInputStream(f);
				wtfReader = new BufferedReaderClean(new InputStreamReader (gzipInputStream));
				bResult = extractWTFData(wtfReader);
			}else{
				String sWTFFile = fileGrepSearch("wtf.*.txt" , aFolder);
				if (!sWTFFile.isEmpty()){
					wtfReader = new BufferedReaderClean(new FileReader(sWTFFile));
					bResult = extractWTFData(wtfReader);
				}
			}
		}catch(Exception e) {
			APLog.e( "WTF : " + e);
			e.printStackTrace();
			return false;
		} finally {
			if (wtfReader != null) {
				wtfReader.close();
			}
			silentClose(f);
		}
		return bResult;
	}

	private boolean anr(String aFolder){
		boolean bResult = true;

		String sSysANRGZ = fileGrepSearch(".*_(app|server)_anr.*txt.gz", aFolder);
		BufferedReaderClean sysANRReader = null;
		FileInputStream f = null;
		try {
			if (!sSysANRGZ.isEmpty()){
				f = new FileInputStream(sSysANRGZ);
				GZIPInputStream gzipInputStream = new GZIPInputStream(f);
				sysANRReader = new BufferedReaderClean(new InputStreamReader (gzipInputStream));
				bResult = extractAnrData(sysANRReader);
			}else{
				String sSysANR = fileGrepSearch(".*_(app|server)_anr.*txt" , aFolder);
				if (!sSysANR.isEmpty()){
					sysANRReader = new BufferedReaderClean(new FileReader(sSysANR));
					bResult = extractAnrData(sysANRReader);
				}
			}
		}catch(Exception e) {
			APLog.e( "anr - general AppANR : " + e);
			e.printStackTrace();
			return false;
		} finally {
			if (sysANRReader != null) {
				sysANRReader.close();
			}
			silentClose(f);
		}
		return bResult;
	}


	private boolean javacrash(String aFolder){
		boolean bResult = true;
		bResult &= parseJavaCrashFile(".*_app_crash.*.txt.gz",".*_app_crash.*.txt",aFolder);
		bResult &= parseJavaCrashFile("system_server_crash.*.txt.gz","system_server_crash.*.txt",aFolder);
		bResult &= parseJavaCrashFile(".*_app_native_crash.*.txt.gz",".*_app_native_crash.*.txt",aFolder, true);
		return bResult;
	}

	private boolean parseJavaCrashFile(String aFileZip, String aFileNormal, String aFolder){
		return parseJavaCrashFile(aFileZip, aFileNormal, aFolder, false);
	}

	private boolean parseJavaCrashFile(String aFileZip, String aFileNormal, String aFolder, boolean nativ){
		boolean bResult = true;
		String sSysAppGZ = fileGrepSearch(aFileZip, aFolder);
		BufferedReaderClean sysAppReader = null;
		FileInputStream f = null;
		try {
			if (!sSysAppGZ.isEmpty()){
				f = new FileInputStream(sSysAppGZ);
				GZIPInputStream gzipInputStream = new GZIPInputStream(f);
				sysAppReader = new BufferedReaderClean(new InputStreamReader (gzipInputStream));
				bResult = extractJavaCrashData(sysAppReader, nativ);
			}else{
				String sSysApp = fileGrepSearch(aFileNormal, aFolder);
				if (!sSysApp.isEmpty()){
					sysAppReader = new BufferedReaderClean(new FileReader(sSysApp));
					bResult = extractJavaCrashData(sysAppReader, nativ);
				}
			}
		}catch(Exception e) {
			APLog.e( "javacrash - parseJavaCrashFile : " + e);
			e.printStackTrace();
			return false;
		} finally {
			if (sysAppReader != null) {
				sysAppReader.close();
			}
			silentClose(f);
		}
		return bResult;
	}

	private boolean extractUIWDTData(BufferedReader aReader){
		boolean bResult = true;

		String sPID= "";
		String sType= "";
		StringBuffer sStack = new StringBuffer();
		boolean bPIDFound = false;
		boolean bTypeFound = false;
		int iStackCount = 0;

		Pattern patternPID = java.util.regex.Pattern.compile(".*Process:.*");
		Pattern patternType = java.util.regex.Pattern.compile(".*Subject:.*");
		Pattern patternStack = java.util.regex.Pattern.compile("^  at.*");

		String sCurLine;
		try {
			while ((sCurLine = aReader.readLine()) != null) {
				String sTmp;
				if (!bPIDFound){
					sTmp = simpleGrepAwk(patternPID, sCurLine, ":", 1);
					if (sTmp != null){
						sPID = sTmp;
						bPIDFound = true;
					}
				}
				if (!bTypeFound){
					sTmp = simpleGrepAwk(patternType, sCurLine, ":", 1);
					if (sTmp != null){
						sType = sTmp;
						bTypeFound = true;
					}
				}

				if (iStackCount<8){
					sTmp = simpleGrepAwk(patternStack, sCurLine, "at ", 1);
					sTmp = simpleAwk(sTmp,"\\(", 0);
					if (sTmp != null){
						if (iStackCount == 0){
							sStack.append(sTmp);
						}
						else{
							sStack.append(" " +  sTmp);
						}
						iStackCount++;
					}
				}
			}
			bResult &= appendToCrashfile("DATA0=" + sPID);
			bResult &= appendToCrashfile("DATA1=" + sType);
			bResult &= appendToCrashfile("DATA2=" + sStack);
		}catch (Exception e) {
			APLog.e( "extractUIWDTData : " + e);
			e.printStackTrace();
			return false;
		}
		return bResult;
	}


	private boolean extractWTFData(BufferedReader aReader){
		boolean bResult = true;

		String sPID= "";
		String sType= "";
		boolean bPIDFound = false;
		boolean bTypeFound = false;

		Pattern patternPID = java.util.regex.Pattern.compile(".*Process:.*");
		Pattern patternType = java.util.regex.Pattern.compile(".*Subject:.*");

		String sCurLine;
		try {
			while ((sCurLine = aReader.readLine()) != null) {
				String sTmp;
				if (!bPIDFound){
					sTmp = simpleGrepAwk(patternPID, sCurLine, ":", 1);
					if (sTmp != null){
						sPID = sTmp;
						bPIDFound = true;
					}
				}
				if (!bTypeFound){
					sTmp = simpleGrepAwk(patternType, sCurLine, ":", 1);
					if (sTmp != null){
						sType = sTmp;
						bTypeFound = true;
					}
				}
			}
			bResult &= appendToCrashfile("DATA0=" + sPID);
			bResult &= appendToCrashfile("DATA1=" + sType);
		}catch (Exception e) {
			APLog.e( "extractWTFData : " + e);
			e.printStackTrace();
			return false;
		}
		return bResult;
	}


	private boolean extractAnrData(BufferedReader aReader){
		boolean bResult = true;

		String sPID= "";
		String sType= "";
		StringBuffer sStack = new StringBuffer();
		String sCPU = "";
		boolean bPIDFound = false;
		boolean bTypeFound = false;
		boolean bCPUFound = false;
		boolean bCMDLineFound = false;
		boolean bMainFound = false;
		int iStackCount = 0;

		Pattern patternPID = java.util.regex.Pattern.compile(".*Process:.*");
		Pattern patternType = java.util.regex.Pattern.compile(".*Subject:.*");
		Pattern patternStack = java.util.regex.Pattern.compile("^  at.*");
		Pattern patternCPU = java.util.regex.Pattern.compile(".*TOTAL.*");
		Pattern patternCMDLine = java.util.regex.Pattern.compile("^Cmd line:.*");
		Pattern patternMain = java.util.regex.Pattern.compile("^\"main\" prio.*");
		String sCurLine;
		try {
			while ((sCurLine = aReader.readLine()) != null) {
				String sTmp;
				if (!bPIDFound){
					sTmp = simpleGrepAwk(patternPID, sCurLine, ":", 1);
					if (sTmp != null){
						sPID = sTmp;
						bPIDFound = true;
					}
				}
				if (!bTypeFound){
					sTmp = simpleGrepAwk(patternType, sCurLine, ":", 1);
					if (sTmp != null){
						sType = sTmp;
						bTypeFound = true;
					}
				}
				if (!bCPUFound){
					sTmp = simpleGrepAwk(patternCPU, sCurLine, "TOTAL", 0);
					if (sTmp != null){
						sCPU = sTmp;
						bCPUFound = true;
					}
				}

				//robustness on stack search, need to find appropriate CMDLINE
				if (!bCMDLineFound){
					sTmp = simpleGrepAwk(patternCMDLine, sCurLine, "", 0);
					if (sTmp != null){
						if (sTmp.contains(sPID)){
							bCMDLineFound = true;
						}
					}
				} else if(!bMainFound){
					sTmp = simpleGrepAwk(patternMain, sCurLine, "", 0);
					if (sTmp != null){
						bMainFound = true;
					}
				} else if (iStackCount<8){
					sTmp = simpleGrepAwk(patternStack, sCurLine, "at ", 1);
					sTmp = simpleAwk(sTmp,"\\(", 0);
					if (sTmp != null){
						if (iStackCount == 0){
							sStack.append(sTmp);
						}
						else{
							sStack.append(" " +  sTmp);
						}
						iStackCount++;
					}
				}
			}

			bResult &= appendToCrashfile("DATA0=" + sPID);
			bResult &= appendToCrashfile("DATA1=" + sType);
			bResult &= appendToCrashfile("DATA2=" + sStack);
			bResult &= appendToCrashfile("DATA3=cpu:" + sCPU);
			aReader.close();
		}catch (Exception e) {
			APLog.e( "extractAnrData : " + e);
			e.printStackTrace();
			return false;
		}
		return bResult;
	}

	private boolean extractJavaCrashData(BufferedReader aReader, boolean nativ){
		boolean bResult = true;
		String sPID= "";
		String sCausedBy= "";
		StringBuffer sStack = new StringBuffer();
		String sPreviousLine = "";
		String sBeforeAt = "";
		String sData1;
		boolean bPIDFound = false;
		int iStackCount = 0;

		Pattern patternPID = java.util.regex.Pattern.compile(".*Process:.*");
		Pattern patternCausedBy = java.util.regex.Pattern.compile("^Caused by:.*");
		Pattern patternStack = java.util.regex.Pattern.compile(".*at .*");
		String sCurLine;
		try {
			while ((sCurLine = aReader.readLine()) != null) {
				String sTmp;
				if (!bPIDFound){
					sTmp = simpleGrepAwk(patternPID, sCurLine, ":", 1);
					if (sTmp != null){
						sPID = sTmp;
						bPIDFound = true;
					}
				}
				//last caused by block should be used, so never stop watching at pattern
				sTmp = simpleGrepAwk(patternCausedBy, sCurLine, ":", 1, true);
				if (sTmp != null){
					sCausedBy = sTmp;
				}
				if (iStackCount<4){
					sTmp = simpleGrepAwk(patternStack, sCurLine, "at ", 1);
					sTmp = simpleAwk(sTmp,"\\(", 0);
					if (sTmp != null){
						if (iStackCount == 0){
							sStack.append(sTmp);
							sBeforeAt = sPreviousLine;
						}
						else{
							sStack.append(" " +  sTmp);
						}
						iStackCount++;
					}
				}
				sPreviousLine = sCurLine;
			}

			bResult &= appendToCrashfile("DATA0=" + filterAdressesPattern(sPID));
			if (!sCausedBy.isEmpty()) {
				sData1 = sCausedBy;
			} else {
				sData1 = sBeforeAt;
			}
			if (nativ) {
				bResult &= appendToCrashfile("DATA1=app_native_crash" + filterAdressesPattern(sData1));
			}
			else {
				String str = filterAdressesPattern(sData1);
				String strPattern = "android.os.TransactionTooLargeException: data parcel size";
				if (str.trim().startsWith(strPattern)) {
					String strPattern2 = "android.os.TransactionTooLargeException";
					bResult &= appendToCrashfile("DATA1=" + strPattern2);
					bResult &= appendToCrashfile("DATA3=" + str.trim().substring(strPattern2.length() + 2));
				} else {
					bResult &= appendToCrashfile("DATA1=" + str);
				}
			}
			bResult &= appendToCrashfile("DATA2=" + filterAdressesPattern(sStack.toString()));
		}catch (Exception e) {
			APLog.e( "extractJavaCrashData : " + e);
			e.printStackTrace();
			return false;
		}
		return bResult;
	}

	private String filterAdressesPattern(String stringToFilter){
		String sResult = stringToFilter;
		Pattern patternAdress8 = java.util.regex.Pattern.compile("@[0-9a-fA-F]{8}");
		Pattern patternAdress16 = java.util.regex.Pattern.compile("@[0-9a-fA-F]{16}");
		sResult = patternAdress16.matcher(sResult).replaceAll("");
		sResult = patternAdress8.matcher(sResult).replaceAll("");
		return sResult;
	}

	private boolean appendToCrashfile(String aStr){
		try{
			myOutput.write(aStr + "\n");
		} catch (Exception e) {
			APLog.e( "appendToCrashfile : " + e);
			e.printStackTrace();
			return false;
		}
		return true;
	}

	private boolean checkGrepInvertGrep(Pattern aPattern,Pattern aInvertPattern, String aLine)
	{
		String sTestGrep = simpleGrepAwk(aPattern, aLine, "", 0);
		if (sTestGrep != null){
			String sTestInvertGrep = simpleGrepAwk(aInvertPattern, aLine, "", 0);
			if (sTestInvertGrep == null){
				return true;
			}else{
				return false;
			}
		}else{
			return false;
		}
	}

	private String simpleGrepAwk(Pattern aPattern, String aLine, String sSeparator, int iReturnIndex){
		return simpleGrepAwk(aPattern, aLine, sSeparator, iReturnIndex, false);
	}

	private String simpleAwk(String aString, String sSeparator, int iReturnIndex){
		return simpleAwk(aString, sSeparator, iReturnIndex, false);
	}

	private String advancedAwk(String aString, String sSeparator, int iReturnIndex){
		String sResult = null;
		if (aString != null){
			String[] splitString = aString.split("(" + sSeparator + ")+" );
			//to manage beginning separator
			if (splitString[0].isEmpty()){
				iReturnIndex++;
			}
			if (splitString.length > iReturnIndex ){
				sResult = splitString[iReturnIndex];
			}
		}
		return sResult;
	}

	private String fileGrepSearch(String aPattern, String aFolder){
		Pattern patternFile = java.util.regex.Pattern.compile(aPattern);

		File searchFolder = new File(aFolder );
		File[] files = searchFolder.listFiles();
		String sFileResult = "";
		if(files!=null) {
			for(File f: files) {
				Matcher matcherFile = patternFile.matcher(f.getName());
				if (matcherFile.find()){
					sFileResult = aFolder + "/" + f.getName();
					break;
				}
			}
		}
		return sFileResult;
	}

	private String simpleGrepAwk(Pattern aPattern, String aLine, String sSeparator, int iReturnIndex, boolean left){
		Matcher simpleMatcher = aPattern.matcher(aLine);
		String sResult = null;
		if (simpleMatcher.find()){
			String sGroup = simpleMatcher.group();
			if (sSeparator.isEmpty()){
				sResult = sGroup;
			}else {
				sResult = simpleAwk(sGroup,sSeparator,iReturnIndex, left);
			}
		}
		return sResult;
	}

	private String simpleAwk(String aString, String sSeparator, int iReturnIndex, boolean left){
		String sResult = null;
		if (aString != null){
			String[] splitString = left?aString.split(sSeparator, iReturnIndex + 1):aString.split(sSeparator);
			if (splitString.length > iReturnIndex ){
				sResult = splitString[iReturnIndex];
			}
		}
		return sResult;
	}

}
