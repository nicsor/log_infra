/* Android Modem Traces and Logs
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
 * Author: Tony Goubert <tonyx.goubert@intel.com>
 */

package com.intel.amtl;

import java.io.IOException;
import java.io.RandomAccessFile;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

public class Trace_in_coredumpActivity extends Activity {

    private Button button_disable_ma_artemis_coredump;
    private Button button_enable_ma_artemis_coredump;
    private Button button_apply_ma_artemis_coredump;
    private Button button_disable_ma_artemis_digrf_coredump;
    private Button button_enable_ma_artemis_digrf_coredump;
    private Button button_apply_ma_artemis_digrf_coredump;
    private Button button_apply_activate_coredump;
    private ProgressDialog progressDialog;
    private TextView coredump_text;
    private int trace_value;
    Runtime rtm=java.lang.Runtime.getRuntime();

    void writeSimple(String iout,String ival) throws IOException {
        RandomAccessFile f = new RandomAccessFile(iout, "rws");
        f.writeBytes(ival);
        f.close();
    }

    private void RebootMessage() {
        coredump_text.setVisibility(View.VISIBLE);
        coredump_text.setText("Your board need a HARDWARE reboot");
    }

    private void trace_unavailable() {
        coredump_text.setVisibility(View.VISIBLE);
        coredump_text.setText("You can't enable it, disable others traces BEFORE");
    }

    private void wrong_trace_enabled() {
        coredump_text.setVisibility(View.VISIBLE);
        coredump_text.setText("DISABLE MA & Artemis traces THEN Enable MA & Artemis & Digrf traces");
    }

    private void trace_not_enabled() {
        coredump_text.setVisibility(View.VISIBLE);
        coredump_text.setText("Enable MA & Artemis & Digrf traces BEFORE");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.trace_in_coredump);

        button_disable_ma_artemis_coredump = (Button) findViewById(R.id.disable_ma_artemis_coredump_button);
        button_enable_ma_artemis_coredump = (Button) findViewById(R.id.enable_ma_artemis_coredump_button);
        button_apply_ma_artemis_coredump = (Button) findViewById(R.id.apply_ma_artemis_coredump_button);
        button_disable_ma_artemis_digrf_coredump = (Button) findViewById(R.id.disable_ma_artemis_digrf_coredump_button);
        button_enable_ma_artemis_digrf_coredump = (Button) findViewById(R.id.enable_ma_artemis_digrf_coredump_button);
        button_apply_ma_artemis_digrf_coredump = (Button) findViewById(R.id.apply_ma_artemis_digrf_coredump_button);
        button_apply_activate_coredump = (Button) findViewById(R.id.apply_activate_coredump_button);
        coredump_text=(TextView) findViewById(R.id.text_coredump);

        /*Get the between instance stored values*/
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        ((CompoundButton) button_disable_ma_artemis_coredump).setChecked(preferences.getBoolean("button_disable_ma_artemis_coredump_value", true));
        ((CompoundButton) button_enable_ma_artemis_coredump).setChecked(preferences.getBoolean("button_enable_ma_artemis_coredump_value", false));
        ((CompoundButton) button_disable_ma_artemis_digrf_coredump).setChecked(preferences.getBoolean("button_disable_ma_artemis_digrf_coredump", true));
        ((CompoundButton) button_enable_ma_artemis_digrf_coredump).setChecked(preferences.getBoolean("button_enable_ma_artemis_digrf_coredump_value", false));
        trace_value = preferences.getInt("trace_value", trace_value);

        /*Listener for button_apply_ma_artemis_coredump*/
        button_apply_ma_artemis_coredump.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if ((((CompoundButton) button_enable_ma_artemis_digrf_coredump).isChecked())||((((CompoundButton) button_enable_ma_artemis_coredump).isChecked()) && (trace_value!=0))) {
                    /*Trace already running ?*/
                    trace_unavailable();
                } else {
                    progressDialog = ProgressDialog.show(Trace_in_coredumpActivity.this, "Please wait...", "Apply MA & Artemis traces in Progress");

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if ((((CompoundButton) button_enable_ma_artemis_coredump).isChecked()) && (trace_value==0)) {
                                    /*Enable MA & Artemis traces*/
                                    trace_value=1;
                                    writeSimple("/dev/gsmtty1","at+trace=1\r\n");
                                    android.os.SystemClock.sleep(1000);
                                    writeSimple("/dev/gsmtty1","at+xsystrace=1,\"bb_sw=1;3g_sw=1\",,\"oct=4\"\r\n");
                                    android.os.SystemClock.sleep(2000);
                                } else {
                                    /*Disable traces*/
                                    trace_value=0;
                                    writeSimple("/dev/gsmtty1","at+trace=0\r\n");
                                    android.os.SystemClock.sleep(1000);
                                    writeSimple("/dev/gsmtty1","at+xsystrace=0\"\r\n");
                                    android.os.SystemClock.sleep(2000);
                                }
                                progressDialog.dismiss();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                    Save_status_coredump();
                }
            }
        });

        /*Listener for button_apply_activate_coredump*/
        button_apply_ma_artemis_digrf_coredump.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if ((((CompoundButton) button_enable_ma_artemis_coredump).isChecked())||((((CompoundButton) button_enable_ma_artemis_digrf_coredump).isChecked()) && (trace_value!=0))) {
                    /*Trace already running ?*/
                    trace_unavailable();
                } else {
                    progressDialog = ProgressDialog.show(Trace_in_coredumpActivity.this, "Please wait...", "Apply MA & Artemis & Digrf traces in Progress");

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (((CompoundButton) button_enable_ma_artemis_digrf_coredump).isChecked()) {
                                    /*Useful for digrf traces*/
                                    trace_value=2;
                                    writeSimple("/dev/gsmtty1","at+xsio=2\r\n");
                                    android.os.SystemClock.sleep(1000);
                                } else {
                                    /*Disable traces*/
                                    trace_value=0;
                                    writeSimple("/dev/gsmtty1","at+xsio=0\r\n");
                                    android.os.SystemClock.sleep(1000);
                                    writeSimple("/dev/gsmtty1","at+trace=0\r\n");
                                    android.os.SystemClock.sleep(1000);
                                    writeSimple("/dev/gsmtty1","at+xsystrace=0\"\r\n");
                                    android.os.SystemClock.sleep(2000);
                                }
                                progressDialog.dismiss();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                    RebootMessage();
                    Save_status_coredump();
                }
            }
        });

        button_apply_activate_coredump.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (((CompoundButton) button_enable_ma_artemis_coredump).isChecked()) {
                    /*Others traces enabled ?*/
                    wrong_trace_enabled();
                } else if (((CompoundButton) button_disable_ma_artemis_digrf_coredump).isChecked()){
                    /*Traces not enabled ?*/
                    trace_not_enabled();
                } else {
                    progressDialog = ProgressDialog.show(Trace_in_coredumpActivity.this, "Please wait...", "Apply MA & Artemis & Digrf traces in Progress");

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                writeSimple("/dev/gsmtty1","at+trace=1\r\n");
                                android.os.SystemClock.sleep(1000);
                                writeSimple("/dev/gsmtty1","at+xsystrace=1,\"digrf=1;bb_sw=1;3g_sw=1\",\"digrf=0x84\",\"oct=4\"\r\n");
                                android.os.SystemClock.sleep(2000);
                                progressDialog.dismiss();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                    Save_status_coredump();
                }
            }
        });
    }

    protected void Save_status_coredump() {
        /*Store values between instances here*/
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        /*Put the values from the UI*/
        boolean button_disable_ma_artemis_coredump_value = ((CompoundButton) button_disable_ma_artemis_coredump).isChecked();
        boolean button_enable_ma_artemis_coredump_value = ((CompoundButton) button_enable_ma_artemis_coredump).isChecked();
        boolean button_disable_ma_artemis_digrf_coredump_value = ((CompoundButton) button_disable_ma_artemis_digrf_coredump).isChecked();
        boolean button_enable_ma_artemis_digrf_coredump_value = ((CompoundButton) button_enable_ma_artemis_digrf_coredump).isChecked();

        /*Value to store*/
        editor.putBoolean("button_disable_ma_artemis_coredump_value", button_disable_ma_artemis_coredump_value);
        editor.putBoolean("button_enable_ma_artemis_coredump_value", button_enable_ma_artemis_coredump_value);
        editor.putBoolean("button_disable_ma_artemis_digrf_coredump_value", button_disable_ma_artemis_digrf_coredump_value);
        editor.putBoolean("button_enable_ma_artemis_digrf_coredump_value", button_enable_ma_artemis_digrf_coredump_value);
        editor.putInt("trace_value", trace_value);

        /*Commit to storage*/
        editor.commit();
    }
}