package de.rki.coronawarnapp.storage

import androidx.lifecycle.MutableLiveData
import be.sciensano.coronalert.service.DummyService
import be.sciensano.coronalert.storage.isTestResultNegative
import de.rki.coronawarnapp.exception.NoRegistrationTokenSetException
import de.rki.coronawarnapp.util.DeviceUIState
import de.rki.coronawarnapp.util.formatter.TestResult
import java.util.Date
import be.sciensano.coronalert.service.submission.SubmissionService as BeSubmissionService

object SubmissionRepository {
    private val TAG: String? = SubmissionRepository::class.simpleName

    val testResultReceivedDate = MutableLiveData(Date())
    val deviceUIState = MutableLiveData(DeviceUIState.UNPAIRED)

    suspend fun refreshUIState() {
        var uiState = DeviceUIState.UNPAIRED

        if (LocalData.numberOfSuccessfulSubmissions() == 1) {
            uiState = DeviceUIState.SUBMITTED_FINAL
        } else {
            if (LocalData.registrationToken() != null) {
                uiState = when {
                    LocalData.isAllowedToSubmitDiagnosisKeys() == true -> {
                        DeviceUIState.PAIRED_POSITIVE
                    }
                    LocalData.isTestResultNegative() == true -> {
                        DeviceUIState.PAIRED_NEGATIVE
                    }
                    else -> fetchTestResult()
                }
            }
        }
        deviceUIState.value = uiState
    }

    private suspend fun fetchTestResult(): DeviceUIState {
        try {
            val testResultResponse = BeSubmissionService.asyncRequestTestResult()
            val testResult = TestResult.fromInt(testResultResponse.result)
            if (testResult == TestResult.POSITIVE) {
                LocalData.isAllowedToSubmitDiagnosisKeys(true)
            }

            if (testResult == TestResult.NEGATIVE) {
                LocalData.isTestResultNegative(true)
            }

            if (testResult == TestResult.POSITIVE || testResult == TestResult.NEGATIVE) {
                BeSubmissionService.asyncSendAck(testResultResponse)
                LocalData.initialTestResultReceivedTimestamp(System.currentTimeMillis())
            } else {
                DummyService.fakeAckRequest()
            }


            return when (testResult) {
                TestResult.NEGATIVE -> DeviceUIState.PAIRED_NEGATIVE
                TestResult.POSITIVE -> DeviceUIState.PAIRED_POSITIVE
                TestResult.PENDING -> DeviceUIState.PAIRED_NO_RESULT
                TestResult.INVALID -> DeviceUIState.PAIRED_ERROR
                TestResult.REDEEMED -> DeviceUIState.PAIRED_REDEEMED
            }
        } catch (err: NoRegistrationTokenSetException) {
            return DeviceUIState.UNPAIRED
        }
    }

    fun setTeletan(teletan: String) {
        LocalData.teletan(teletan)
    }
}
