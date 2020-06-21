package de.georgsieber.customerdb;

import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import de.georgsieber.customerdb.model.Customer;
import de.georgsieber.customerdb.model.CustomerFile;
import de.georgsieber.customerdb.model.Voucher;

public class CustomerDatabaseApi extends AsyncTask<Void, Void, String> {

    static String MANAGED_API = "https://customerdb.sieber.systems/api.php";

    private WeakReference<MainActivity> mMainActivityReference;

    private String mPurchaseToken;
    private String mApiUrl;
    private String mUsername;
    private String mPassword;

    private List<Customer> mCustomers;
    private List<Voucher> mVouchers;

    CustomerDatabaseApi(MainActivity context, String purchaseToken, String username, String password, List<Customer> customers, List<Voucher> vouchers) {
        mMainActivityReference = new WeakReference<>(context);
        mPurchaseToken = purchaseToken;
        mApiUrl = MANAGED_API;
        mUsername = username;
        mPassword = password;
        mCustomers = customers;
        mVouchers = vouchers;
    }
    CustomerDatabaseApi(MainActivity context, String purchaseToken, String url, String username, String password, List<Customer> customers, List<Voucher> vouchers) {
        mMainActivityReference = new WeakReference<>(context);
        mPurchaseToken = purchaseToken;
        mApiUrl = url;
        mUsername = username;
        mPassword = password;
        mCustomers = customers;
        mVouchers = vouchers;
    }

    @Override
    protected String doInBackground(Void... params) {
        try {
            putCustomers();
            readCustomers();
            return "";
        } catch(Exception e) {
            return e.getMessage();
        }
    }

    private void putCustomers() throws Exception {
        try {
            JSONArray jarrayCustomers = new JSONArray();
            for(Customer c : mCustomers) {
                JSONArray jsonFiles = new JSONArray();
                for(CustomerFile file : c.getFiles()) {
                    JSONObject jsonFile = new JSONObject();
                    jsonFile.put("name", file.mName);
                    jsonFile.put("content", Base64.encodeToString(file.mContent, Base64.NO_WRAP));
                    jsonFiles.put(jsonFile);
                }

                JSONObject jc = new JSONObject();
                jc.put("id", c.mId);
                jc.put("title", c.mTitle);
                jc.put("first_name", c.mFirstName);
                jc.put("last_name", c.mLastName);
                jc.put("phone_home", c.mPhoneHome);
                jc.put("phone_mobile", c.mPhoneMobile);
                jc.put("phone_work", c.mPhoneWork);
                jc.put("email", c.mEmail);
                jc.put("street", c.mStreet);
                jc.put("zipcode", c.mZipcode);
                jc.put("city", c.mCity);
                jc.put("country", c.mCountry);
                jc.put("birthday", c.mBirthday == null ? JSONObject.NULL : CustomerDatabase.storageFormat.format(c.mBirthday)+" 00:00:00");
                jc.put("customer_group", c.mCustomerGroup);
                jc.put("newsletter", c.mNewsletter ? 1 : 0);
                jc.put("notes", c.mNotes);
                jc.put("image", (c.mImage==null ? JSONObject.NULL : Base64.encodeToString(c.mImage, Base64.NO_WRAP)));
                jc.put("consent", Base64.encodeToString(jsonFiles.toString().getBytes(), Base64.NO_WRAP));
                jc.put("custom_fields", c.mCustomFields);
                jc.put("last_modified", CustomerDatabase.storageFormatWithTime.format(c.mLastModified));
                jc.put("removed", c.mRemoved);
                jarrayCustomers.put(jc);
            }

            JSONArray jarrayVouchers = new JSONArray();
            for(Voucher v : mVouchers) {
                JSONObject jc = new JSONObject();
                jc.put("id", v.mId);
                jc.put("original_value", v.mOriginalValue);
                jc.put("current_value", v.mCurrentValue);
                jc.put("voucher_no", v.mVoucherNo);
                jc.put("from_customer", v.mFromCustomer);
                jc.put("for_customer", v.mForCustomer);
                jc.put("issued", CustomerDatabase.storageFormatWithTime.format(v.mIssued));
                jc.put("valid_until", v.mValidUntil == null ? JSONObject.NULL : CustomerDatabase.storageFormatWithTime.format(v.mValidUntil));
                jc.put("redeemed", v.mRedeemed == null ? JSONObject.NULL : CustomerDatabase.storageFormatWithTime.format(v.mRedeemed));
                jc.put("notes", v.mNotes);
                jc.put("last_modified", CustomerDatabase.storageFormatWithTime.format(v.mLastModified));
                jc.put("removed", v.mRemoved);
                jarrayVouchers.put(jc);
            }

            JSONObject jparams = new JSONObject();
            jparams.put("playstore_token", mPurchaseToken);
            jparams.put("username", mUsername);
            jparams.put("password", mPassword);
            jparams.put("customers", jarrayCustomers);
            jparams.put("vouchers", jarrayVouchers);

            JSONObject jroot = new JSONObject();
            jroot.put("jsonrpc", "2.0");
            jroot.put("id", 1);
            jroot.put("method", "customerdb.put");
            jroot.put("params", jparams);

            String result = openConnection(jroot.toString());
            //Log.e("API", result);

            try {
                JSONObject jresult = new JSONObject(result);
                if(jresult.isNull("result") || !jresult.getBoolean("result")) {
                    throw new Exception(jresult.getString("error"));
                }
            } catch(JSONException e) {
                throw new Exception(result);
            }
        } catch(JSONException ex) {
            throw new Exception(ex.getMessage());
        }
    }

    private void readCustomers() throws Exception {
        MainActivity activity = mMainActivityReference.get();
        try {
            JSONObject jparams = new JSONObject();
            jparams.put("playstore_token", mPurchaseToken);
            jparams.put("username", mUsername);
            jparams.put("password", mPassword);

            JSONObject jroot = new JSONObject();
            jroot.put("jsonrpc", "2.0");
            jroot.put("id", 1);
            jroot.put("method", "customerdb.read");
            jroot.put("params", jparams);

            String result = openConnection(jroot.toString());
            //Log.e("API", jroot.toString());
            //Log.e("API", result);

            try {
                JSONObject jresult = new JSONObject(result);
                JSONObject jresults = jresult.getJSONObject("result");
                JSONArray jcustomers = jresults.getJSONArray("customers");
                JSONArray jvouchers = jresults.getJSONArray("vouchers");

                activity.mDb.beginTransaction();
                activity.mDb.truncateCustomer();
                activity.mDb.truncateVoucher();

                for(int i = 0; i < jcustomers.length(); i++) {
                    JSONObject jo = jcustomers.getJSONObject(i);
                    Customer c = new Customer();
                    Iterator<String> iter = jo.keys();
                    while(iter.hasNext()) {
                        String key = iter.next();
                        if(jo.isNull(key)) continue;
                        String value = jo.getString(key);
                        c.putCustomerAttribute(key, value);
                    }
                    //Log.e("API", c.mId+"");
                    if(c.mId > 0) activity.mDb.addCustomer(c);
                }
                for(int i = 0; i < jvouchers.length(); i++) {
                    JSONObject jo = jvouchers.getJSONObject(i);
                    Voucher v = new Voucher();
                    Iterator<String> iter = jo.keys();
                    while(iter.hasNext()) {
                        String key = iter.next();
                        if(jo.isNull(key)) continue;
                        String value = jo.getString(key);
                        v.putVoucherAttribute(key, value);
                    }
                    if(v.mId > 0) activity.mDb.addVoucher(v);
                }

                activity.mDb.commitTransaction();
                activity.mDb.endTransaction();
            } catch(Exception e) {
                activity.mDb.endTransaction();
                try {
                    JSONObject jresult = new JSONObject(result);
                    throw new Exception("Get Values Failed ("+e.getMessage()+") - "+jresult.getString("error"));
                } catch(JSONException e2) {
                    throw new Exception("Get Values Failed ("+e.getMessage()+") - "+result);
                }
            }
        } catch(JSONException ex) {
            throw new Exception(ex.getMessage());
        }
    }

    private String openConnection(String send) throws Exception {
        String text = "";
        BufferedReader reader = null;

        try {

            URL url = new URL(mApiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(send);
            wr.flush();

            int statusCode = conn.getResponseCode();
            if(statusCode == 200)
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            else
                reader = new BufferedReader(new InputStreamReader((conn).getErrorStream()));
            StringBuilder sb2 = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb2.append(line).append("\n");
            }
            text = sb2.toString();

            if(text.equals("")) text = statusCode + " " + conn.getResponseMessage();

        } catch(Exception ex) {

            throw new Exception(ex.getMessage());

        } finally {

            if(reader != null) try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        return text;
    }

    @Override
    protected void onPostExecute(String result) {
        // get a reference to the activity if it is still there
        MainActivity activity = mMainActivityReference.get();
        if(activity == null) return;
        activity.refreshCustomersFromLocalDatabase();
        activity.refreshVouchersFromLocalDatabase();
        if(result.trim().equals("")) {
            activity.dialogSyncSuccess();
            MainActivity.setChangesSynced(activity);
        } else {
            activity.dialogSyncFail(result);
        }
        activity.refreshSyncIcon();
    }

}
