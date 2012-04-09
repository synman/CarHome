package com.google.android.mms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

public class APNHelper {

    /**
     * APN types for data connections.  These are usage categories for an APN
     * entry.  One APN entry may support multiple APN types, eg, a single APN
     * may service regular internet traffic ("default") as well as MMS-specific
     * connections.<br/>
     * APN_TYPE_ALL is a special type to indicate that this APN entry can
     * service all data connections.
     */
    static final String APN_TYPE_ALL = "*";
    /** APN type for default data traffic */
    static final String APN_TYPE_DEFAULT = "default";
    /** APN type for MMS traffic */
    static final String APN_TYPE_MMS = "mms";
    /** APN type for SUPL assisted GPS */
    static final String APN_TYPE_SUPL = "supl";
    /** APN type for DUN traffic */
    static final String APN_TYPE_DUN = "dun";
    /** APN type for HiPri traffic */
    static final String APN_TYPE_HIPRI = "hipri";
    
   public class APN {
      public String MMSCenterUrl = "";
      public String MMSPort = "";
      public String MMSProxy = ""; 
   }

private Context context;

   public APNHelper(final Context context) {
      this.context = context;
   }   

   public List<APN> getMMSApns() {     
      final Cursor apnCursor = this.context.getContentResolver().query(Uri.withAppendedPath(Carriers.CONTENT_URI, "current"), null, null, null, null);
      if ( apnCursor == null ) {
         return Collections.EMPTY_LIST;
      } else {
         final List<APN> results = new ArrayList<APN>();         
         while ( apnCursor.moveToNext() ) {
            final String type = apnCursor.getString(apnCursor.getColumnIndex(Carriers.TYPE));
            if ( !TextUtils.isEmpty(type) && ( type.contains(APN_TYPE_ALL) || type.contains(APN_TYPE_MMS)) ) {
               final String mmsc = apnCursor.getString(apnCursor.getColumnIndex(Carriers.MMSC));
               final String mmsProxy = apnCursor.getString(apnCursor.getColumnIndex(Carriers.MMSPROXY));
               final String port = apnCursor.getString(apnCursor.getColumnIndex(Carriers.MMSPORT));                  
               final APN apn = new APN();
               apn.MMSCenterUrl = mmsc;
               apn.MMSProxy = mmsProxy;
               apn.MMSPort = port.trim().length() > 0 ? port : "80";
               results.add(apn);
            }
         }                   
         apnCursor.close();
         return results;
      }
   }
   
   public static final class Carriers implements BaseColumns {
       /**
        * The content:// style URL for this table
        */
       public static final Uri CONTENT_URI =
           Uri.parse("content://telephony/carriers");

       /**
        * The default sort order for this table
        */
       public static final String DEFAULT_SORT_ORDER = "name ASC";

       public static final String NAME = "name";

       public static final String APN = "apn";

       public static final String PROXY = "proxy";

       public static final String PORT = "port";

       public static final String MMSPROXY = "mmsproxy";

       public static final String MMSPORT = "mmsport";

       public static final String SERVER = "server";

       public static final String USER = "user";

       public static final String PASSWORD = "password";

       public static final String MMSC = "mmsc";

       public static final String MCC = "mcc";

       public static final String MNC = "mnc";

       public static final String NUMERIC = "numeric";

       public static final String TYPE = "type";

       public static final String CURRENT = "current";
   }

}