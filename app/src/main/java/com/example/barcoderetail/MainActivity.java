package com.example.barcoderetail;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.barcode.common.Barcode;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    PreviewView preview; TextView status,product,code,qty,cart,summary;
    EditText price,discount,gst;
    int quantity=1; String currentCode="", currentName="";
    double subtotal=0;
    ArrayList<String> items=new ArrayList<>();
    ExecutorService executor=Executors.newSingleThreadExecutor();
    android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    long lastScan=0; String lastCode="";
    SharedPreferences prefs;

    @Override public void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main);
        preview=findViewById(R.id.preview); status=findViewById(R.id.status);
        product=findViewById(R.id.product); code=findViewById(R.id.code); qty=findViewById(R.id.qty);
        cart=findViewById(R.id.cart); summary=findViewById(R.id.summary);
        price=findViewById(R.id.price); discount=findViewById(R.id.discount); gst=findViewById(R.id.gst);
        prefs=getSharedPreferences("catalog",MODE_PRIVATE);

        findViewById(R.id.plus).setOnClickListener(v->{quantity++; qty.setText("Qty "+quantity);});
        findViewById(R.id.minus).setOnClickListener(v->{if(quantity>1)quantity--; qty.setText("Qty "+quantity);});
        findViewById(R.id.addBill).setOnClickListener(v->addBill());
        findViewById(R.id.clear).setOnClickListener(v->clearBill());
        findViewById(R.id.share).setOnClickListener(v->shareBill());
        findViewById(R.id.addProductBtn).setOnClickListener(v->saveCurrentProduct());
        findViewById(R.id.historyBtn).setOnClickListener(v->showHistory());
        discount.setOnFocusChangeListener((v,has)->{if(!has)updateSummary();});
        gst.setOnFocusChangeListener((v,has)->{if(!has)updateSummary();});

        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera();
        else ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},77);
    }

    void startCamera(){
        ListenableFuture<ProcessCameraProvider> f=ProcessCameraProvider.getInstance(this);
        f.addListener(()->{
            try{
                ProcessCameraProvider p=f.get();
                Preview pr=new Preview.Builder().build();
                pr.setSurfaceProvider(preview.getSurfaceProvider());
                ImageAnalysis a=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                a.setAnalyzer(executor,proxy->{
                    if(proxy.getImage()==null){proxy.close();return;}
                    InputImage im=InputImage.fromMediaImage(proxy.getImage(),proxy.getImageInfo().getRotationDegrees());
                    BarcodeScanning.getClient().process(im).addOnSuccessListener(bs->{
                        for(Barcode x:bs){String raw=x.getRawValue(); if(!TextUtils.isEmpty(raw)){
                            long now=System.currentTimeMillis();
                            if(!raw.equals(lastCode)||now-lastScan>3000){lastCode=raw;lastScan=now;scan(raw);}
                            break;
                        }} }).addOnCompleteListener(x->proxy.close());
                });
                p.unbindAll();p.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,pr,a);
            }catch(Exception e){status.setText("Camera error");}
        },ContextCompat.getMainExecutor(this));
    }

    void scan(String raw){
        currentCode=raw; currentName=""; runOnUiThread(()->{
            code.setText("Barcode: "+raw); product.setText("Product: checking...");
            status.setText("Looking up product...");
        });
        String local=prefs.getString(raw,"");
        if(!local.isEmpty()){
currentName = local.split("\\|", 2)[0];
String[] p = local.split("\\|");
            if(p.length>1)price.setText(p[1]);
            product.setText("Product: "+currentName); status.setText("Local shop catalog match");
            return;
        }
        executor.execute(()->{
            try{
                URL u=new URL("https://world.openfoodfacts.org/api/v3/product/"+URLEncoder.encode(raw,"UTF-8")+".json?fields=product_name,product_name_en,brands");
                HttpURLConnection c=(HttpURLConnection)u.openConnection();
                c.setRequestProperty("User-Agent","BarcodeBillerPro/2.0");
                c.setConnectTimeout(6000); c.setReadTimeout(6000);
                BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder s=new StringBuilder();String line;while((line=r.readLine())!=null)s.append(line);r.close();
                JSONObject root=new JSONObject(s.toString());
                if(root.optInt("status",0)==1){
                    JSONObject p=root.optJSONObject("product");
                    String n=p==null?"Product found":p.optString("product_name","");
                    if(n.isEmpty()&&p!=null)n=p.optString("product_name_en","");
                    if(n.isEmpty())n="Product found";
                    String br=p==null?"":p.optString("brands","");
                    if(!br.isEmpty())n+=" ("+br+")";
                    currentName=n; String fn=n;
                    runOnUiThread(()->{product.setText("Product: "+fn);status.setText("Product found. Enter price.");});
                }else notFound();
            }catch(Exception e){notFound();}
        });
    }

    void notFound(){runOnUiThread(()->{currentName="Unknown product";product.setText("Product: Not found");status.setText("Enter price manually.");});}

    void addBill(){
        String ps=price.getText().toString().trim();
        if(currentCode.isEmpty()||ps.isEmpty()){Toast.makeText(this,"Scan product and enter price",Toast.LENGTH_SHORT).show();return;}
        try{
            double unit=Double.parseDouble(ps); double line=unit*quantity;
            String name=currentName.isEmpty()?"Unknown product":currentName;
            items.add(name+" | "+currentCode+" | Qty "+quantity+" | ₹"+String.format(Locale.US,"%.2f",line));
            subtotal+=line; cart.setText(TextUtils.join("\n\n",items)); updateSummary();
            saveHistory(name,currentCode,line);
            quantity=1;qty.setText("Qty 1");price.setText("");
            status.setText("Added. Scan next product.");
        }catch(Exception e){Toast.makeText(this,"Invalid price",Toast.LENGTH_SHORT).show();}
    }

    void updateSummary(){
        double d=read(discount.getText().toString()), g=read(gst.getText().toString());
        double disc=subtotal*d/100.0, taxable=subtotal-disc, tax=taxable*g/100.0, total=taxable+tax;
        summary.setText(String.format(Locale.US,"Subtotal: ₹%.2f\nDiscount: ₹%.2f\nGST: ₹%.2f\nTOTAL: ₹%.2f",subtotal,disc,tax,total));
    }
    double read(String s){try{return s.isEmpty()?0:Double.parseDouble(s);}catch(Exception e){return 0;}}

    void clearBill(){items.clear();subtotal=0;cart.setText("No items");updateSummary();}

    void saveCurrentProduct(){
        if(currentCode.isEmpty()){Toast.makeText(this,"Scan a product first",Toast.LENGTH_SHORT).show();return;}
        String n=currentName.isEmpty()?"Unknown product":currentName;
        String p=price.getText().toString().trim();
        if(p.isEmpty()){Toast.makeText(this,"Enter price before saving",Toast.LENGTH_SHORT).show();return;}
        prefs.edit().putString(currentCode,n+"|"+p).apply();
        Toast.makeText(this,"Saved to local shop catalog",Toast.LENGTH_SHORT).show();
    }

    void saveHistory(String n,String c,double amount){
        String old=prefs.getString("history","");
        String row=new Date()+"\n"+n+"\n"+c+"\n₹"+String.format(Locale.US,"%.2f",amount);
        String next=row+"\n---\n"+old;
        if(next.length()>10000)next=next.substring(0,10000);
        prefs.edit().putString("history",next).apply();
    }

    void showHistory(){
        String h=prefs.getString("history","No scan history yet.");
        new android.app.AlertDialog.Builder(this).setTitle("Scan History").setMessage(h)
                .setPositiveButton("OK",null).show();
    }

    void shareBill(){
        if(items.isEmpty()){Toast.makeText(this,"Bill is empty",Toast.LENGTH_SHORT).show();return;}
        double d=read(discount.getText().toString()),g=read(gst.getText().toString());
        double disc=subtotal*d/100,tax=(subtotal-disc)*g/100,total=subtotal-disc+tax;
        String text="BARCODE BILLER\n\n"+TextUtils.join("\n",items)+
                String.format(Locale.US,"\n\nSubtotal ₹%.2f\nDiscount ₹%.2f\nGST ₹%.2f\nTOTAL ₹%.2f",subtotal,disc,tax,total);
        Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,text);
        startActivity(Intent.createChooser(i,"Share bill"));
    }

    @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){
        super.onRequestPermissionsResult(r,p,g);if(r==77&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startCamera();
    }
    @Override protected void onDestroy(){super.onDestroy();executor.shutdown();}
}
