package com.hzy.whotextme.ui.main

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hzy.whotextme.R
import kotlinx.android.synthetic.main.item_sms_layout.view.*
import kotlinx.android.synthetic.main.main_fragment.*
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

@RuntimePermissions
class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    private fun configureViews() {
        Log.d("TAG", "configureViews: ");
        recyclerView.adapter = SMSListAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        btnScanSMS.setOnClickListener {
            scanSMSWithPermissionCheck()
        }
    }


    @SuppressLint("SetTextI18n")
    @NeedsPermission(android.Manifest.permission.READ_SMS)
    fun scanSMS() {
        val progressBar = ProgressBar(requireContext())
        progressBar.isIndeterminate = true

        val params = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID

        main.addView(progressBar, params)

        thread {
            val rawSMS = getSmsInPhone()
            val smsInPhone = rawSMS.map {
                val list = matchPattern(it)
                Log.d("TAG", list.toString())
                list
            }.flatten().groupBy {
                it
            }.mapValues {
                it.value.size
            }.map {
                SMSListAdapter.SmsItem(it.key, it.value)
            }.sortedBy { it.num }
                .reversed()


            Log.d("TAG", "flatten $smsInPhone")
            activity?.runOnUiThread {
                progressBar.isVisible = false
                tagCountPrompt.text = "短信数:${rawSMS.size} 结果：${smsInPhone.size}"
                (recyclerView.adapter as SMSListAdapter).data = smsInPhone
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        configureViews()


    }

    @SuppressLint("LongLogTag")
    fun getSmsInPhone(): ArrayList<String> {
        Log.d("TAG", "getSmsInPhone")


        val SMS_URI_ALL = "content://sms/" // 所有短信
        val SMS_URI_INBOX = "content://sms/inbox" // 收件箱
        val SMS_URI_SEND = "content://sms/sent" // 已发送
        val SMS_URI_DRAFT = "content://sms/draft" // 草稿
        val SMS_URI_OUTBOX = "content://sms/outbox" // 发件箱
        val SMS_URI_FAILED = "content://sms/failed" // 发送失败
        val SMS_URI_QUEUED = "content://sms/queued" // 待发送列表


        val smsList = ArrayList<String>()



        try {
            val uri = Uri.parse(SMS_URI_ALL)
            val contentResolver = context?.contentResolver ?: return arrayListOf()
            val projection = arrayOf("_id", "address", "person", "body", "date", "type")
            var cur = contentResolver.query(uri, projection, null, null, "date desc") // 获取手机内部短信
            // 获取短信中最新的未读短信
            Log.d("SMS", "${cur?.columnCount}")
            // Cursor cur = getContentResolver().query(uri, projection,
            val smsBuilder = StringBuilder()
            // "read = ?", new String[]{"0"}, "date desc");
            if (cur!!.moveToFirst()) {
                val index_Address = cur.getColumnIndex("address")
                val index_Person = cur!!.getColumnIndex("person")
                val index_Body = cur!!.getColumnIndex("body")
                val index_Date = cur!!.getColumnIndex("date")
                val index_Type = cur!!.getColumnIndex("type")

                do {
                    val strAddress = cur!!.getString(index_Address)
                    val intPerson = cur!!.getInt(index_Person)
                    val strbody = cur!!.getString(index_Body)
                    val longDate = cur!!.getLong(index_Date)
                    val intType = cur!!.getInt(index_Type)

                    val dateFormat = SimpleDateFormat(
                        "yyyy-MM-dd hh:mm:ss"
                    )
                    val d = Date(longDate)
                    val strDate = dateFormat.format(d)

                    var strType = ""
                    if (intType == 1) {
                        strType = "接收"
                    } else if (intType == 2) {
                        strType = "发送"
                    } else if (intType == 3) {
                        strType = "草稿"
                    } else if (intType == 4) {
                        strType = "发件箱"
                    } else if (intType == 5) {
                        strType = "发送失败"
                    } else if (intType == 6) {
                        strType = "待发送列表"
                    } else if (intType == 0) {
                        strType = "所以短信"
                    } else {
                        strType = "null"
                    }

                    smsBuilder.append("[ ")
                    smsBuilder.append("$strAddress, ")
                    smsBuilder.append("$intPerson, ")
                    smsBuilder.append("$strbody, ")
                    smsBuilder.append("$strDate, ")
                    smsBuilder.append(strType)
                    smsBuilder.append(" ]\n\n")
                    smsList.add(smsBuilder.toString())
                    smsBuilder.clear()
                } while (cur.moveToNext())

                if (!cur.isClosed) {
                    cur.close()
                    cur = null
                }
            } else {
                smsBuilder.append("no result!")
            }

            smsBuilder.append("getSmsInPhone has executed!")

        } catch (ex: SQLiteException) {
            Log.d("SQLiteException in getSmsInPhone", ex.message)
        }


        Log.d("TAG", "smsList.size: ${smsList.size}")

        return smsList
    }


    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(smsItem: SMSListAdapter.SmsItem) {
            itemView.tvWho.text = smsItem.who
            itemView.tvNum.text = smsItem.num.toString()
        }

    }

    class SMSListAdapter : RecyclerView.Adapter<ViewHolder>() {

        var data: List<SmsItem> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sms_layout, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int {
            return data.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(data[position])
        }


        data class SmsItem(val who: String, val num: Int)
    }


    fun matchPattern(str: String): ArrayList<String> {
        Log.d("TAG", "matchPattern() called with: str = [$str]")
        if (str.isNullOrEmpty()) {
            return arrayListOf()
        }

        val sb = StringBuilder()
        val tags = ArrayList<String>()
        var append = false
        str.forEachIndexed { index, c ->
            if (c == '】') {
                append = false
                tags.add(sb.toString())
            }

            if (append) {
                sb.append(c)
            }

            if (c == '【') {
                append = true
                sb.clear()
            }

        }

        return tags


    }


    private fun constructSMS() {

//        val tagsString = StringBuilder()
//        tags.joinTo(tagsString, ",")
//
//        SMS(tagsString.toString(), content = str)

    }

    data class SMS(val tags: String, val content: String)


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        this.onRequestPermissionsResult(requestCode, grantResults)
    }
}

