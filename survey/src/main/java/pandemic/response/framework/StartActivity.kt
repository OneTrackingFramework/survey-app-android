package pandemic.response.framework

import android.app.TaskStackBuilder
import android.content.Intent
import android.os.Bundle
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import pandemic.response.framework.MainActivity.Companion.SURVEY_ID
import pandemic.response.framework.databinding.ActionContainerBinding
import pandemic.response.framework.steps.PERMISSION_ACTIVITY_RECOGNITION
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.PermissionRequest


class StartActivity : BaseActivity(), EasyPermissions.PermissionCallbacks {

    companion object {
        const val KEY_SURVEY_ID_NAME = "surveyNameId"
        const val PERMISSION_REQUEST_CODE = 1001
    }

    private val binding by lazy {
        ActionContainerBinding.inflate(layoutInflater)
    }

    private val stepsManager by lazy {
        (application as SurveyBaseApp).stepsManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        checkRegistration()
    }

    private fun checkRegistration() {
        if (userManager.isRegistered()) {
            checkStepCounterRights()
        } else {
            notRegistered()
        }
    }

    @AfterPermissionGranted(PERMISSION_REQUEST_CODE)
    private fun checkStepCounterRights() {
        if (EasyPermissions.hasPermissions(
                        this,
                        PERMISSION_ACTIVITY_RECOGNITION
                )
        ) {
            // Already have permission, do the thing
            launch(true)
        } else {
            stepsManager.stop()
            // Do not have permissions, request them now
            val request = PermissionRequest.Builder(
                this,
                PERMISSION_REQUEST_CODE,
                PERMISSION_ACTIVITY_RECOGNITION
            )
                .setRationale(R.string.steps_permission_rationale)
                .setPositiveButtonText(R.string.button_ok)
                .setNegativeButtonText(R.string.button_cancel)
                    .build()

            EasyPermissions.requestPermissions(request)
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
    }

    override fun onPermissionsDenied(
            requestCode: Int,
            perms: List<String?>
    ) {
        launch(false)
        // (Optional) Check whether the user denied any permissions and checked "NEVER ASK AGAIN."
        // This will display a dialog directing them to enable the permission in app settings.
//        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
//            AppSettingsDialog.Builder(this).build().show()
//        }
    }

    private fun launch(stepCounterEnable: Boolean) {
        if (stepCounterEnable) stepsManager.start()

        val surveyNameId: String? = intent.getStringExtra(KEY_SURVEY_ID_NAME)
        if (surveyNameId != null) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(SURVEY_ID, surveyNameId)
            }
            TaskStackBuilder.create(this).addNextIntentWithParentStack(intent).startActivities()
        } else {
            startActivity(Intent(this, SurveyListActivity::class.java))
        }
    }

    private fun notRegistered(): Unit = binding.run {
        actionTitle.text = getString(R.string.invalid_access)
        actionTitle.isVisible = false
        actionMessage.text = HtmlCompat.fromHtml(getNoAccessContent(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        button.isVisible = false
        progressBar.isVisible = false
    }

    private fun getNoAccessContent(): String =
            assets.open("no_access.html").bufferedReader().use {
                it.readText()
            }

}