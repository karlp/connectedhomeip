package com.google.chip.chiptool.clusterclient

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.ClusterIDMapping
import chip.devicecontroller.InvokeCallback
import chip.devicecontroller.OpenCommissioningCallback
import chip.devicecontroller.model.InvokeElement
import chip.tlv.AnonymousTag
import chip.tlv.TlvWriter
import com.google.chip.chiptool.ChipClient
import com.google.chip.chiptool.GenericChipDeviceListener
import com.google.chip.chiptool.R
import com.google.chip.chiptool.databinding.MultiAdminClientFragmentBinding
import kotlinx.coroutines.*

class MultiAdminClientFragment : Fragment() {
  private val deviceController: ChipDeviceController
    get() = ChipClient.getDeviceController(requireContext())

  private lateinit var scope: CoroutineScope

  private lateinit var addressUpdateFragment: AddressUpdateFragment

  private var _binding: MultiAdminClientFragmentBinding? = null
  private val binding get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = MultiAdminClientFragmentBinding.inflate(inflater, container, false)
    scope = viewLifecycleOwner.lifecycleScope

    deviceController.setCompletionListener(ChipControllerCallback())

    addressUpdateFragment =
      childFragmentManager.findFragmentById(R.id.addressUpdateFragment) as AddressUpdateFragment

    binding.basicCommissioningMethodBtn.setOnClickListener { scope.launch { sendBasicCommissioningCommandClick() } }
    binding.enhancedCommissioningMethodBtn.setOnClickListener { scope.launch { sendEnhancedCommissioningCommandClick() } }
    binding.revokeBtn.setOnClickListener { scope.launch { sendRevokeCommandClick() } }

    return binding.root
  }

  override fun onStart() {
    super.onStart()
    // TODO: use the discriminator and setupPinCode that was used to commission the device
    val testDiscriminator = "3840"
    val testSetupPinCode = 20202021L
    val testDuration = 180
    binding.discriminatorEd.setText(testDiscriminator)
    binding.setupPinCodeEd.setText(testSetupPinCode.toString())
    binding.timeoutEd.setText(testDuration.toString())
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  inner class ChipControllerCallback : GenericChipDeviceListener() {
    override fun onConnectDeviceComplete() {}

    override fun onCommissioningComplete(nodeId: Long, errorCode: Int) {
      Log.d(TAG, "onCommissioningComplete for nodeId $nodeId: $errorCode")
    }

    override fun onNotifyChipConnectionClosed() {
      Log.d(TAG, "onNotifyChipConnectionClosed")
    }

    override fun onCloseBleComplete() {
      Log.d(TAG, "onCloseBleComplete")
    }

    override fun onError(error: Throwable?) {
      Log.d(TAG, "onError: $error")
    }
  }

  private suspend fun sendBasicCommissioningCommandClick() {
    val testDuration = binding.timeoutEd.text.toString().toInt()
    deviceController.openPairingWindowCallback(
      ChipClient.getConnectedDevicePointer(
        requireContext(),
        addressUpdateFragment.deviceId
      ), testDuration,
      object:OpenCommissioningCallback {
        override fun onError(status: Int, deviceId: Long) {
          showMessage("OpenBasicCommissioning Fail! \nDevice ID : $deviceId\nErrorCode : $status")
        }

        override fun onSuccess(deviceId: Long, manualPairingCode: String?, qrCode: String?) {
          showMessage("OpenBasicCommissioning Success! \n Node ID: $deviceId")
        }
      }
    )
  }

  private suspend fun sendEnhancedCommissioningCommandClick() {
    val testDuration = binding.timeoutEd.text.toString().toInt()
    val testIteration = 1000
    val devicePointer =
      ChipClient.getConnectedDevicePointer(requireContext(), addressUpdateFragment.deviceId)

    var setupPinCode: Long? = null
    if (!binding.setupPinCodeEd.text.toString().isEmpty()) {
      setupPinCode = binding.setupPinCodeEd.text.toString().toULong().toLong()
    }
    deviceController.openPairingWindowWithPINCallback(
      devicePointer, testDuration, testIteration.toLong(),
      binding.discriminatorEd.text.toString().toInt(), setupPinCode,
      object:OpenCommissioningCallback {
        override fun onError(status: Int, deviceId: Long) {
          showMessage("OpenCommissioning Fail! \nDevice ID : $deviceId\nErrorCode : $status")
        }

        override fun onSuccess(deviceId: Long, manualPairingCode: String?, qrCode: String?) {
          showMessage("OpenCommissioning Success! \n Node ID: $deviceId\n\tManual : $manualPairingCode\n\tQRCode : $qrCode")
        }
      }
    )
  }

  private suspend fun sendRevokeCommandClick() {
    val timedInvokeTimeout = 10000
    // TODO : Need to be implement poj-to-tlv
    val tlvWriter = TlvWriter()
    tlvWriter.startStructure(AnonymousTag)
    tlvWriter.endStructure()
    val invokeElement = InvokeElement.newInstance(0
            , ClusterIDMapping.AdministratorCommissioning.ID
            , ClusterIDMapping.AdministratorCommissioning.Command.RevokeCommissioning.id
            , tlvWriter.getEncoded(), null)

    deviceController.invoke(object: InvokeCallback {
      override fun onError(ex: Exception?) {
        showMessage("Revoke Commissioning  failure $ex")
        Log.e(TAG, "Revoke Commissioning  failure", ex)
      }

      override fun onResponse(invokeElement: InvokeElement?, successCode: Long) {
        Log.e(TAG, "onResponse : $invokeElement, Code : $successCode")
        showMessage("Revoke Commissioning success")
      }

    }, getConnectedDevicePointer(), invokeElement, timedInvokeTimeout, 0)
  }

  private suspend fun getConnectedDevicePointer(): Long {
    return ChipClient.getConnectedDevicePointer(requireContext(), addressUpdateFragment.deviceId)
  }

  private fun showMessage(msg: String) {
    requireActivity().runOnUiThread {
      binding.multiAdminClusterCommandStatus.text = msg
    }
  }

  companion object {
    private const val TAG = "MultiAdminClientFragment"
    fun newInstance(): MultiAdminClientFragment = MultiAdminClientFragment()
  }
}
