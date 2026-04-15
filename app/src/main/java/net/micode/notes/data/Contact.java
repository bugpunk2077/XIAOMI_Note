/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;


/**
 * 联系人数据访问工具类。
 * 负责通过电话号码向系统联系人提供者（ContentProvider）查询联系人姓名，
 * 并提供基础的内存缓存功能以减少重复的数据库查询开销。
 */
public class Contact {

    /**
     * 联系人姓名缓存集合。
     * 键为标准电话号码字符串，值为对应的联系人显示名称。
     */
    private static HashMap<String, String> sContactCache;

    private static final String TAG = "Contact";

    /**
     * SQLite 查询选择条件语句。
     * 用于在系统联系人数据库中精确匹配电话号码。
     * 包含对电话号码相等性的判断，MIME类型校验，以及利用 phone_lookup 表进行最小匹配过滤。
     * 注意：使用 '+' 作为动态替换最小匹配值的占位符。
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
            + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
            + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据指定的电话号码获取联系人显示名称。
     * 优先从静态内存缓存中读取；若未命中缓存，则执行数据库查询并更新缓存。
     *
     * @param context     应用程序或活动上下文，用于获取 ContentResolver。
     * @param phoneNumber 需要查询的电话号码。
     * @return 匹配的联系人姓名。如果未找到匹配项或发生数据库读取异常，则返回 null。
     */
    public static String getContact(Context context, String phoneNumber) {
        // 延迟初始化缓存实例
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 检查缓存命中情况
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 构造动态 SQL 查询条件，将 '+' 占位符替换为实际的来电号码最小匹配字符串
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        // 执行 ContentResolver 查询
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        // 验证游标有效性并移动至第一条记录
        if (cursor != null && cursor.moveToFirst()) {
            try {
                // 提取首列（DISPLAY_NAME）数据并写入缓存
                String name = cursor.getString(0);
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                // 确保在数据读取完毕或发生异常时释放游标资源
                cursor.close();
            }
        } else {
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            // 严重缺陷位置：如果 cursor != null 但是 moveToFirst() 返回 false，游标在此处未被关闭
            return null;
        }
    }
}