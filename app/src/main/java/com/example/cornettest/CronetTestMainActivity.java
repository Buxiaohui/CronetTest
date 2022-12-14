package com.example.cornettest;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.cornettest.databinding.ActivityMainBinding;

import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.HostResolverLzd;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CronetTestMainActivity extends AppCompatActivity {
    private static final String TAG = "CronetTestMainActivity";
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cronetRequest(CronetTestMainActivity.this);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private ByteArrayOutputStream mBytesReceived = new ByteArrayOutputStream();
    private WritableByteChannel mReceiveChannel = Channels.newChannel(mBytesReceived);
    CronetEngine engine = null;
    final ExecutorService executorService = Executors.newFixedThreadPool(4);

    public void cronetRequest(Context context) {
        if (null == engine) {
            File cacheFile = new File(context.getCacheDir(), "cronet");
            if (!cacheFile.exists()) {
                cacheFile.mkdir();
            }
            engine = new CronetEngine.Builder(this)
                    .setStoragePath(cacheFile.getAbsolutePath())
                    .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK_NO_HTTP, 100 * 1024)
                    .enableHttp2(true)
                    .enableQuic(true)
                    .addQuicHint("quic.nginx.org",443,443)
                    .enableBrotli(true)
                    .setHostResolver(new HostResolverLzd() {
                        @Override
                        public List<InetAddress> resolve(String s) throws UnknownHostException {
                            Log.d(TAG, "resolve,s:" + s);
                            List<InetAddress> list = new ArrayList<>();
                             InetAddress address = InetAddress.getByName(s);
                            Log.d(TAG, "resolve,address from local host:" + address);
                            list.add(address);
                            list.add(InetAddress.getByName("112.97.53.30"));
                            return list;
                        }
                    })
                    .build();
            File directory = new File(context.getCacheDir().getAbsolutePath());
            File file = null;
            try {
                file = File.createTempFile("cronet", "json", directory);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                engine.startNetLogToFile(file.getPath(), true);
            } catch (Exception e) {
                Log.e(TAG, "startNetLogToFile,e:" + e);
            }
        }
        String url_0 = "https://httpbin.org/get";
        String url_1 = "https://quic.nginx.org/";
        engine.newUrlRequestBuilder(url_1, new UrlRequest.Callback() {

            @Override
            public void onRedirectReceived(
                    UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
                Log.d(TAG, "onRedirectReceived");
                request.followRedirect();
            }

            @Override
            public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
                Log.i(TAG, "Response Started");
                request.read(ByteBuffer.allocateDirect(64 * 1024));
            }

            @Override
            public void onReadCompleted(UrlRequest request, UrlResponseInfo info, ByteBuffer byteBuffer) {
                byteBuffer.flip();
                Log.i(TAG, "onReadCompleted");

                try {
                    mReceiveChannel.write(byteBuffer);
                } catch (IOException e) {
                    Log.i(TAG, "IOException during ByteBuffer read. Details: ", e);
                }
                byteBuffer.clear();
                request.read(byteBuffer);
            }

            @Override
            public void onSucceeded(UrlRequest request, UrlResponseInfo info) {
                Log.i(TAG, "onSucceeded,Request Completed");
                Log.i(TAG, "onSucceeded,status code is " + info.getHttpStatusCode());
                Log.i(TAG, "onSucceeded,total received bytes is:" + info.getReceivedByteCount());
                Log.d(TAG, "onSucceeded,mBytesReceived:" + mBytesReceived.toString());
                Log.d(TAG, "onSucceeded,UrlRequest:" + request);
                Log.d(TAG, "onSucceeded,UrlResponseInfo:" + info);
                Log.d(TAG, "onSucceeded,info.getHttpStatusCode:" + info.getHttpStatusCode());
                Log.d(TAG, "onSucceeded,info.getAllHeaders:" + info.getAllHeaders());
                Log.d(TAG, "onSucceeded,info.getHttpStatusText:" + info.getHttpStatusText());
                Log.d(TAG, "onSucceeded,info.getNegotiatedProtocol:" + info.getNegotiatedProtocol());
                Log.d(TAG, "onSucceeded,info.getUrl:" + info.getUrl());
                Log.d(TAG, "onSucceeded,info.getProxyServer:" + info.getProxyServer());
                Log.d(TAG, "onSucceeded,info.wasCached:" + info.wasCached());
                Log.d(TAG, "onSucceeded,info.getUrlChain:" + info.getUrlChain());
            }

            @Override
            public void onFailed(UrlRequest request, UrlResponseInfo info, CronetException error) {
                Log.i(TAG, "****** onFailed, CronetException is: " + error.getMessage());
                Log.i(TAG, "****** onFailed, UrlRequest is: " + request);
                Log.i(TAG, "****** onFailed, UrlResponseInfo is: " + info);

            }
        }, executorService).build().start();
    }
}