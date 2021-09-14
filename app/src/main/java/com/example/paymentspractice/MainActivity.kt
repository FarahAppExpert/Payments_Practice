package com.example.paymentspractice

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.paymentspractice.util.Json
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wallet.*
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToLong

class MainActivity : AppCompatActivity() {

    private lateinit var paymentsClient: PaymentsClient
    private val shippingCost = (90 * 1000000).toLong()

    private lateinit var garmentList: JSONArray
    private lateinit var selectedGarment: JSONObject


    private val LOAD_PAYMENT_DATA_REQUEST_CODE = 991


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        selectedGarment = fetchRandomGarment()
        displayGarment(selectedGarment)


        paymentsClient = PaymentsUtil.createPaymentsClient(this)
        possiblyShowGooglePayButton()

        googlePayButton.setOnClickListener { requestPayment() }
    }

    private fun possiblyShowGooglePayButton() {

        val isReadyToPayJson = PaymentsUtil.isReadyToPayRequest() ?: return
        val request = IsReadyToPayRequest.fromJson(isReadyToPayJson.toString()) ?: return

        val task = paymentsClient.isReadyToPay(request)
        task.addOnCompleteListener { completedTask ->
            try {
                completedTask.getResult(ApiException::class.java)?.let(::setGooglePayAvailable)
            } catch (exception: ApiException) {
                Log.w("isReadyToPay failed", exception)
            }
        }
    }

    private fun setGooglePayAvailable(available: Boolean) {
        if (available) {
            googlePayButton.visibility = View.VISIBLE
        } else {
            Toast.makeText(
                this,
                "Unfortunately, Google Pay is not available on this device",
                Toast.LENGTH_LONG).show();
        }
    }

    private fun requestPayment() {

        googlePayButton.isClickable = false


        val garmentPriceMicros = (selectedGarment.getDouble("price") * 1000000).roundToLong()
        val price = (garmentPriceMicros + shippingCost).microsToString()

        val paymentDataRequestJson = PaymentsUtil.getPaymentDataRequest(price)
        if (paymentDataRequestJson == null) {
            Log.e("RequestPayment", "Can't fetch payment data request")
            return
        }
        val request = PaymentDataRequest.fromJson(paymentDataRequestJson.toString())

        if (request != null) {
            AutoResolveHelper.resolveTask(
                paymentsClient.loadPaymentData(request), this, LOAD_PAYMENT_DATA_REQUEST_CODE)
        }
    }


    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {

            LOAD_PAYMENT_DATA_REQUEST_CODE -> {
                when (resultCode) {
                    Activity.RESULT_OK ->
                        data?.let { intent ->
                            PaymentData.getFromIntent(intent)?.let(::handlePaymentSuccess)
                        }
                    Activity.RESULT_CANCELED -> {

                    }

                    AutoResolveHelper.RESULT_ERROR -> {
                        AutoResolveHelper.getStatusFromIntent(data)?.let {
                            handleError(it.statusCode)
                        }
                    }
                }

                googlePayButton.isClickable = true
            }
        }
    }


    private fun handlePaymentSuccess(paymentData: PaymentData) {
        val paymentInformation = paymentData.toJson() ?: return

        try {

            val paymentMethodData = JSONObject(paymentInformation).getJSONObject("paymentMethodData")

            if (paymentMethodData
                    .getJSONObject("tokenizationData")
                    .getString("type") == "PAYMENT_GATEWAY" && paymentMethodData
                    .getJSONObject("tokenizationData")
                    .getString("token") == "examplePaymentMethodToken") {

                AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("Gateway name set to \"example\" - please modify " +
                            "Constants.java and replace it with your own gateway.")
                    .setPositiveButton("OK", null)
                    .create()
                    .show()
            }

            val billingName = paymentMethodData.getJSONObject("info")
                .getJSONObject("billingAddress").getString("name")
            Log.d("BillingName", billingName)

            Toast.makeText(this, getString(R.string.payments_show_name, billingName), Toast.LENGTH_LONG).show()


            Log.d("GooglePaymentToken", paymentMethodData
                .getJSONObject("tokenizationData")
                .getString("token"))

        } catch (e: JSONException) {
            Log.e("handlePaymentSuccess", "Error: " + e.toString())
        }

    }

    private fun handleError(statusCode: Int) {
        Log.w("loadPaymentData failed", String.format("Error code: %d", statusCode))
    }

    private fun fetchRandomGarment() : JSONObject {
        if (!::garmentList.isInitialized) {
            garmentList = Json.readFromResources(this, R.raw.tshirts)
        }

        val randomIndex:Int = Math.round(Math.random() * (garmentList.length() - 1)).toInt()
        return garmentList.getJSONObject(randomIndex)
    }

    private fun displayGarment(garment:JSONObject) {
        detailTitle.setText(garment.getString("title"))
        detailPrice.setText("\$${garment.getString("price")}")

        val escapedHtmlText:String = Html.fromHtml(garment.getString("description")).toString()
        detailDescription.setText(Html.fromHtml(escapedHtmlText))

        val imageUri = "@drawable/${garment.getString("image")}"
        val imageResource = resources.getIdentifier(imageUri, null, packageName)
        detailImage.setImageResource(imageResource)
    }
}








