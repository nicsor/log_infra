/* Crash Report (CLOTA)
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
 * Author: Jeremy Rocher <jeremyx.rocher@intel.com>
 */

package com.intel.crashreport;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class HistoryEventFile {

	private File histFile;
	private Scanner scanner;

	public HistoryEventFile() throws FileNotFoundException {
		histFile = openFile();
		scanner = new Scanner(histFile);
	}

	public Boolean hasNext() {
		return scanner.hasNext();
	}

	public String getNextEvent() {
		String line;

		while(scanner.hasNext()){
			line = scanner.nextLine();
			if (line.charAt(0) != '#') {
				return line;
			}
		}

		return "";
	}

	private File openFile() {
		String path = new String("/data/" + "logs/history_event");
		File histFile = new File(path);

		if (!histFile.canRead())
			Log.w("HistoryEventFile: can't read file : " + path);

		return histFile;
	}

}