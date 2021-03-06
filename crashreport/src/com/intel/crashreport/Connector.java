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

package com.intel.crashreport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.UserHandle;
import android.preference.PreferenceManager;

import com.intel.crashreport.CrashReportService.ServiceMsg;
import com.intel.crashtoolserver.bean.Event;
import com.intel.crashtoolserver.bean.FileInfo;
import com.intel.crashreport.StartServiceActivity.ServiceToActivityMsg;

public class Connector {

	private static final String wifiSecurity = "WPA";
	private static final int WIFI_CONNECTION_TIME_OUT = 60000;
	private static final int WIFI_AVAILABLE_TIME_OUT = 10000;
	private static final int SERVER_CONNECTION_TIME_OUT = 60000;

	private CrashReport app;
	private Context mCtx;
	private Socket mSocket = null;
	private BufferedReader mInputStream = null;
	private PrintWriter mOutputStream = null;
	private ObjectOutputStream mObjectOutputStream = null;
	private Handler serviceHandler;
	private Timer mTimer;
	private Boolean scanInProgress = false;
	private Boolean wifiWaitForConnect = false;
	private List<ScanResult> mScanResults;
	private WifiManager wm;

	public Connector(Context context) {
		mCtx = context;
		this.app = (CrashReport)mCtx.getApplicationContext();
	}

	public Connector(Context context, Handler serviceHandler) {
		mCtx = context;
		this.serviceHandler = serviceHandler;
		this.app = (CrashReport)mCtx.getApplicationContext();
	}

	public boolean isTryingToConnect(){
		return app.isTryingToConnect();
	}

	public void setTryingToConnect(Boolean s){
		app.setTryingToConnect(s);
	}

	public void setupServerConnection() throws UnknownHostException, IOException, InterruptedIOException {
		ApplicationPreferences prefs = new ApplicationPreferences(mCtx);
		String serverAddress = prefs.getServerAddress();
		int serverPort = prefs.getServerPort();

		if (mSocket != null)
			closeServerConnection();

		mSocket = getSecureSocket();

		if (mSocket == null)
			throw new IOException("mSocket == null");
		mSocket.setSoTimeout(SERVER_CONNECTION_TIME_OUT);
		InetSocketAddress serverAddressPort = new InetSocketAddress(serverAddress, serverPort);
		mSocket.connect(serverAddressPort, SERVER_CONNECTION_TIME_OUT);
		mInputStream = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
		if (mInputStream == null)
			throw new IOException("mInputStream == null");
		mOutputStream = new PrintWriter(mSocket.getOutputStream());
		if (mOutputStream == null)
			throw new IOException("mOutputStream == null");
		mObjectOutputStream = new ObjectOutputStream(mSocket.getOutputStream());
		String readAck = mInputStream.readLine();
		if ((readAck != null) && readAck.contentEquals("ACK"))
			Log.d("Connector: Connected to server");
		else
			throw new IOException("Server doesn't respond ACK");
	}

	public void closeServerConnection() throws IOException {
		if ((mObjectOutputStream != null) && (mSocket != null) && !mSocket.isOutputShutdown()) {
			mObjectOutputStream.writeObject("END");
			mObjectOutputStream.flush();
		}
		if (mSocket != null) {
			try {
				mSocket.close();
			}
			finally {
				mSocket = null;
			}
		}
		if (mInputStream != null) {
			try {
				mInputStream.close();
			}
			finally {
				mInputStream = null;
			}
		}
		if (mOutputStream != null) {
			try {
				mOutputStream.close();
			}
			finally {
				mOutputStream = null;
			}
		}
		if (mObjectOutputStream != null) {
			try {
				mObjectOutputStream.close();
			}
			finally {
				mObjectOutputStream = null;
			}
		}
		Log.d("Connector: Disconnected from server");
	}

	public Boolean getDataConnectionAvailability() {
		ConnectivityManager cm = (ConnectivityManager)mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm!= null && cm.getBackgroundDataSetting()) {
			NetworkInfo net = cm.getActiveNetworkInfo();
			if (net != null) {
				if (net.isConnected())
					return true;
			}
		}
		return false;
	}

	public Boolean getWifiConnectionAvailability() {
		ConnectivityManager cm = (ConnectivityManager)mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
		if(cm != null) {
			NetworkInfo wifiNetInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
			if (wifiNetInfo != null)
				return wifiNetInfo.isConnected();
		}
		return false;
	}

	public void getWifiAvailability() {
		wm = (WifiManager)mCtx.getSystemService(Context.WIFI_SERVICE);
		mTimer = new Timer();
		mTimer.schedule(wifiTimeOut, WIFI_AVAILABLE_TIME_OUT);
		registerReceiver();
		serviceHandler.post(checkWifiStateToAvailable);
	}

	public void getConnectionState() {
		Boolean connected = false;
		wm = (WifiManager)mCtx.getSystemService(Context.WIFI_SERVICE);
		String wifiSsid = getInternalWifiSsid();
		String wmSsid;
		if ((wm != null) && wm.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
			WifiInfo wInfo = wm.getConnectionInfo();
			if (wInfo != null) {
				wmSsid = wInfo.getSSID();
				if ((wmSsid != null) && (wmSsid.equals(wifiSsid)))
					connected = true;
			}
		}
		if (connected)
			serviceHandler.sendEmptyMessage(ServiceMsg.wifiConnectedInternal);
		else
			serviceHandler.sendEmptyMessage(ServiceMsg.wifiNotConnected);
	}

	private Socket getSecureSocket() {
		InputStream caInput = mCtx.getResources().openRawResource(R.raw.crashtool);

		try {
			CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
			Certificate ca = cf.generateCertificate(caInput);

			String keyStoreType = KeyStore.getDefaultType();
			KeyStore keyStore = KeyStore.getInstance(keyStoreType);
			keyStore.load(null, null);
			keyStore.setCertificateEntry("ca", ca);

			String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
			tmf.init(keyStore);

			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, tmf.getTrustManagers(), null);

			return context.getSocketFactory().createSocket();
		} catch (KeyStoreException e) {
			Log.d("Connector: Could not set up socket - " + e);
		} catch (NoSuchAlgorithmException e) {
			Log.d("Connector: Could not set up socket - " + e);
		} catch (CertificateException e) {
			Log.d("Connector: Could not set up socket - " + e);
		} catch (KeyManagementException e) {
			Log.d("Connector: Could not set up socket - " + e);
		} catch (IOException e) {
			Log.d("Connector: Could not set up socket - " + e);
		} catch (NoSuchProviderException e) {
			Log.d("Connector: Could not set up socket - " + e);
		} finally {
			try {
				caInput.close();
			} catch (IOException e) {
				Log.d("Connector: Could not close input stream - " + e);
			}
		}

		return null;
	}

	public void connect() {
		wm = (WifiManager)mCtx.getSystemService(Context.WIFI_SERVICE);
		mTimer = new Timer();
		mTimer.schedule(wifiTimeOut, WIFI_CONNECTION_TIME_OUT);
		setTryingToConnect(true);
		registerReceiver();
		serviceHandler.post(checkWifiStateToConnect);
	}

	public void disconnect() {
		wm = (WifiManager)mCtx.getSystemService(Context.WIFI_SERVICE);
		String wifiSsid = getInternalWifiSsid();
		String wmSsid;
		if ((wm != null) && wm.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
			WifiInfo wInfo = wm.getConnectionInfo();
			if (wInfo != null) {
				wmSsid = wInfo.getSSID();
				if ((wmSsid != null) && (wmSsid.equals(wifiSsid))) {
					disableInternalWifi();
					wm.disconnect();
				}
			}
			wm.setWifiEnabled(false);
		}
		serviceHandler.sendEmptyMessage(ServiceMsg.wifiDisconnected);
	}

	public Boolean sendEvent(Event event) {
		return sendEventSocket(event);
	}

	private void disableInternalWifi() {
		WifiConfiguration wifiConf = getWifiConfigFromConfiguredNetworks(getInternalWifiSsid());
		if (wifiConf != null)
			wm.disableNetwork(wifiConf.networkId);
	}

	private TimerTask wifiTimeOut = new TimerTask() {
		@Override
		public void run() {
			Log.d("Connector: WifiTimeOut");
			scanInProgress = false;
			mCtx.unregisterReceiver(wifiStateReceiver);
			disableInternalWifi();
			wm.setWifiEnabled(false);
			if (isTryingToConnect()) {
				setTryingToConnect(false);
				wifiWaitForConnect = false;
				serviceHandler.sendEmptyMessage(ServiceMsg.wifiConnectTimeOut);
			} else {
				serviceHandler.sendEmptyMessage(ServiceMsg.internalwifiNotAvailable);
			}
		}
	};

	private BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
				Log.d("Connector: Wifi state change");
				if (isTryingToConnect()) {
					serviceHandler.removeCallbacks(checkWifiStateToConnect);
					serviceHandler.post(checkWifiStateToConnect);
				} else {
					serviceHandler.post(checkWifiStateToAvailable);
				}
			} else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
				if (scanInProgress) {
					Log.d("Connector: Scan results available");
					mScanResults = wm.getScanResults();
					if (isTryingToConnect()) {
						serviceHandler.removeCallbacks(checkInternalWifiAvailableConnectOrRetry);
						serviceHandler.post(checkInternalWifiAvailableConnectOrRetry);
					} else {
						serviceHandler.removeCallbacks(checkInternalWifiAvailability);
						serviceHandler.post(checkInternalWifiAvailability);
					}
					scanInProgress = false;
				}
			} else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(intent.getAction())) {
				if (wifiWaitForConnect) {
					Log.d("Connector: Supplicant state Changed");
					SupplicantState supState =  intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
					if (supState != null) {
						if (supState.equals(SupplicantState.COMPLETED)) {
							Log.d("Connector: Supplicant state OK :" + supState.toString());
							serviceHandler.removeCallbacks(checkWifiStateToConnect);
							serviceHandler.postDelayed(checkWifiStateToConnect, 500);
							wifiWaitForConnect = false;
						} else {
							Log.d("Connector: Supplicant state :" + supState.toString());
						}
					} else {
						Log.w("Connector: supState = NULL");
					}
				}
			}
			if (WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION.equals(intent.getAction())) {
				Log.d("Connector: BD INT : SUPPLICANT_CONNECTION_CHANGE_ACTION : EXTRA_SUPPLICANT_CONNECTED:" +
						intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false));
			}
			if (WifiManager.NETWORK_IDS_CHANGED_ACTION.equals(intent.getAction())) {
				Log.d("Connector: BD INT : NETWORK_IDS_CHANGED_ACTION");
			}
			if (WifiManager.RSSI_CHANGED_ACTION.equals(intent.getAction())) {
				Log.d("Connector: BD INT : RSSI_CHANGED_ACTION : " + intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, 0));
			}
		}
	};

	private void registerReceiver() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		filter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
		filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
		mCtx.registerReceiver(wifiStateReceiver, filter);
	}

	private WifiConfiguration createWifiConfiguration() {
		WifiConfiguration wfc = new WifiConfiguration();
		String wifiSsid = getInternalWifiSsid();
		String wifiPass = PreferenceManager.getDefaultSharedPreferences(mCtx).getString("wifiPassPref", "");

		wfc.SSID = "\"".concat(wifiSsid).concat("\"");
		wfc.status = WifiConfiguration.Status.ENABLED;
		wfc.priority = 40;

		wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
		wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
		wfc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
		wfc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);

		if (wifiSecurity.equals("OPEN")) {
			wfc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
			wfc.allowedAuthAlgorithms.clear();
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
		} else if (wifiSecurity.equals("WEP")) {
			wfc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
			wfc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
			wfc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
			wfc.wepKeys[0] = "\"".concat(wifiPass).concat("\"");
			wfc.wepTxKeyIndex = 0;
		} else if (wifiSecurity.equals("WPA")) {
			wfc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
			wfc.preSharedKey = "\"".concat(wifiPass).concat("\"");
		}

		return wfc;
	}

	private Runnable checkWifiStateToConnect = new Runnable() {
		public void run() {
			ConnectivityManager cm = (ConnectivityManager)mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
			int wifiState = wm.getWifiState();
			switch (wifiState) {
			case WifiManager.WIFI_STATE_DISABLED: {
				Log.d("Connector:checkWifiState: WIFI_STATE_DISABLED");
				setTryingToConnect(true);
				wm.setWifiEnabled(true);

				break;
			}
			case WifiManager.WIFI_STATE_DISABLING: {
				Log.d("Connector:checkWifiState: WIFI_STATE_DISABLING");
				setTryingToConnect(true);
				break;
			}
			case WifiManager.WIFI_STATE_ENABLED: {
				Log.d("Connector:checkWifiState: WIFI_STATE_ENABLED");
				setTryingToConnect(true);
				if(cm == null)
					break;
				NetworkInfo nInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
				if (nInfo == null)
					break;
				NetworkInfo.State nState = nInfo.getState();
				if (nState == NetworkInfo.State.CONNECTED) {
					Log.d("Connector:checkWifiState: WIFI NETWORK_STATE CONNECTED");
					setTryingToConnect(false);
					scanInProgress = false;
					wifiWaitForConnect = false;
					mCtx.unregisterReceiver(wifiStateReceiver);
					mTimer.cancel();
					serviceHandler.sendEmptyMessage(ServiceMsg.wifiConnectedInternal);
				} else if (nState == NetworkInfo.State.CONNECTING) {
					Log.d("Connector:checkWifiState: WIFI NETWORK_STATE CONNECTING");
					scanInProgress = true;
					serviceHandler.postDelayed(checkWifiStateToConnect, 10);
				} else if (nState == NetworkInfo.State.DISCONNECTED) {
					Log.d("Connector:checkWifiState: WIFI NETWORK_STATE DISCONNECTED");
					scanInProgress = true;
					wm.startScan();
				} else if (nState == NetworkInfo.State.DISCONNECTING) {
					Log.d("Connector:checkWifiState: WIFI NETWORK_STATE DISCONNECTING");
					scanInProgress = true;
					wm.startScan();
				} else if (nState == NetworkInfo.State.SUSPENDED) {
					Log.d("Connector:checkWifiState: WIFI NETWORK_STATE SUSPENDED");
					scanInProgress = true;
					wm.startScan();
				} else if (nState == NetworkInfo.State.UNKNOWN) {
					Log.d("Connector:checkWifiState: WIFI NETWORK_STATE UNKNOWN");
					scanInProgress = true;
					wm.startScan();
				}
				break;
			}
			case WifiManager.WIFI_STATE_ENABLING: {
				Log.d("Connector:checkWifiState: WIFI_STATE_ENABLING");
				setTryingToConnect(true);
				break;
			}
			case WifiManager.WIFI_STATE_UNKNOWN: {
				Log.d("Connector:checkWifiState: WIFI_STATE_UNKNOWN");
				setTryingToConnect(true);
				wm.setWifiEnabled(true);
				break;
			}
			default: {
				Log.w("Connector:checkWifiState: default");
				setTryingToConnect(true);
				wm.setWifiEnabled(true);
				break;
			}
			}
		}
	};

	private Runnable checkWifiStateToAvailable = new Runnable() {
		public void run() {
			int wifiState = wm.getWifiState();
			switch (wifiState) {
			case WifiManager.WIFI_STATE_DISABLED: {
				Log.d("Connector:checkWifiAvailability: WIFI_STATE_DISABLED");
				setTryingToConnect(false);
				wm.setWifiEnabled(true);
				break;
			}
			case WifiManager.WIFI_STATE_DISABLING: {
				Log.d("Connector:checkWifiAvailability: WIFI_STATE_DISABLING");
				break;
			}
			case WifiManager.WIFI_STATE_ENABLED: {
				Log.d("Connector:checkWifiAvailability: WIFI_STATE_ENABLED");
				setTryingToConnect(false);
				scanInProgress = true;
				wm.startScan();
				break;
			}
			case WifiManager.WIFI_STATE_ENABLING: {
				Log.d("Connector:checkWifiAvailability: WIFI_STATE_ENABLING");
				setTryingToConnect(false);
				break;
			}
			case WifiManager.WIFI_STATE_UNKNOWN: {
				Log.d("Connector:checkWifiAvailability: WIFI_STATE_UNKNOWN");
				setTryingToConnect(false);
				wm.setWifiEnabled(true);
				break;
			}
			default: {
				Log.w("Connector:checkWifiAvailability: default");
				setTryingToConnect(false);
				wm.setWifiEnabled(true);
				break;
			}
			}
		}
	};

	private Boolean checkWifiAvailable(String wifiSsid) {
		if ((mScanResults != null) && (!mScanResults.isEmpty()))
			for (ScanResult result : mScanResults) {
				if (result.SSID.equals(wifiSsid)) {
					return true;
				}
			}
		return false;
	}

	private Runnable checkInternalWifiAvailableConnectOrRetry = new Runnable() {
		Boolean wifiAvailable;
		String wifiSsid;
		public void run() {
			wifiSsid = getInternalWifiSsid();
			wifiAvailable = checkWifiAvailable(wifiSsid);
			if (wifiAvailable) {
				Log.d("Connector: " + wifiSsid + " available");
				serviceHandler.post(connectToNetwork);
			} else {
				Log.d("Connector: " + wifiSsid + " not available, retry scan");
				scanInProgress = true;
				wm.startScan();
			}
		}
	};

	private Runnable checkInternalWifiAvailability = new Runnable() {
		Boolean wifiAvailable;
		String wifiSsid;
		public void run() {
			wifiSsid = getInternalWifiSsid();
			wifiAvailable = checkWifiAvailable(wifiSsid);
			if (wifiAvailable) {
				Log.d("Connector: " + wifiSsid + " available");
				mCtx.unregisterReceiver(wifiStateReceiver);
				scanInProgress = false;
				mTimer.cancel();
				disableInternalWifi();
				wm.setWifiEnabled(false);
				serviceHandler.sendEmptyMessage(ServiceMsg.internalwifiAvailable);
			} else {
				Log.d("Connector: " + wifiSsid + " not available, retry scan");
				scanInProgress = true;
				wm.startScan();
			}
		}
	};

	private String getInternalWifiSsid() {
		return PreferenceManager.getDefaultSharedPreferences(mCtx).getString("wifiSsidPref", "");
	}

	private WifiConfiguration getWifiConfigFromConfiguredNetworks(String wifiSsid) {
		wifiSsid = "\"".concat(wifiSsid).concat("\"");
		List<WifiConfiguration> mWifiConfigs = wm.getConfiguredNetworks();
		if (mWifiConfigs != null) {
			for (WifiConfiguration config : mWifiConfigs) {
				if (config.SSID.equals(wifiSsid)) {
					return config;
				}
			}
		}
		return null;
	}

	private Runnable connectToNetwork = new Runnable() {
		public void run() {
			Log.d("Connector:connectToNetwork");
			String wifiSsid = getInternalWifiSsid();
			WifiConfiguration validConf = getWifiConfigFromConfiguredNetworks(wifiSsid);
			if (validConf != null) {
				Log.d("Connector:connectToNetwork : Wifi already configured");
				if (wm.enableNetwork(validConf.networkId, true)) {
					Log.d("Connector:connectToNetwork : Network enabled");
					wifiWaitForConnect = true;
				}
			} else {
				Log.d("Connector:connectToNetwork : Configure wifi");
				WifiConfiguration wifiConfig = createWifiConfiguration();
				int networkId = wm.addNetwork(wifiConfig);
				wm.saveConfiguration();
				if (networkId != -1)
					if (wm.enableNetwork(networkId, true)) {
						Log.d("Connector:connectToNetwork : Network enabled");
						wifiWaitForConnect = true;
					}
			}
			if (!wifiWaitForConnect) {
				Log.d("Connector:connectToNetwork : Connection fail");
				serviceHandler.post(checkWifiStateToConnect);
			}
		}
	};

	private Boolean sendEventSocket(Event event) {
		String serverMsg;

		if (mObjectOutputStream == null || mInputStream == null) {
			Log.w("sendEventSocket: invalid context!");
			return false;
		}

		try {
			mObjectOutputStream.writeObject(event);
			serverMsg = mInputStream.readLine();
		} catch (IOException e) {
			return false;
		}

		return checkAck(serverMsg);
	}

	public Boolean sendLogsFile(FileInfo fileInfo, Thread t) throws InterruptedException {
		String serverMsg;
		BufferedInputStream bis = null;
		if (mObjectOutputStream == null || mSocket == null || fileInfo == null) {
			Log.w("sendLogsFile: invalid context!");
			return false;
		}

		try {
			mObjectOutputStream.writeObject(fileInfo);

			BufferedOutputStream bos = new BufferedOutputStream(mSocket.getOutputStream());

			byte data[] = new byte[1024];
			int count;
			long fileSize;
			long readBytes = 0;

			fileSize = fileInfo.getSize();
			bis = new BufferedInputStream(new FileInputStream(fileInfo.getPath()));
			while ((count = bis.read(data)) != -1) {
				bos.write(data, 0, count);
				readBytes += count;
				if (fileSize > 0 && app.isActivityBounded()){
					Intent intent = new Intent(ServiceToActivityMsg.uploadProgressBar);

					intent.putExtra("progressValue", (int) ((readBytes*100)/fileSize));
					mCtx.sendBroadcastAsUser(intent, UserHandle.CURRENT);
				}
				if (t == null || t.isInterrupted()) {
					throw new InterruptedException();
				}
			}
			bos.flush();

			if (!mSocket.isClosed() && !mSocket.isInputShutdown()) {
				serverMsg = mInputStream.readLine();
				if (checkAck(serverMsg))
					return true;
			}
		} catch (IOException e) {
			Log.w(Log.getStackTraceString(e));
			return false;
		} finally {
			if (bis != null) {
				try {
					bis.close();
				} catch (IOException e) {
					Log.w("IOException : " + e.getMessage());
				}
			}
		}
		return false;
	}

	private Boolean checkAck(String serverMsg) {
		if (serverMsg == null || !serverMsg.equals("ACK")) {
			return false;
		}
		return true;
	}

}
