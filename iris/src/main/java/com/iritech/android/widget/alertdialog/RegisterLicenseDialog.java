package com.iritech.android.widget.alertdialog;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.iritech.iris.LicenseInfo;
import com.iritech.iris.R;
import com.iritech.iris.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class RegisterLicenseDialog extends DialogFragment {
    private Intent mIntent;
    private int mRequestCode;

    public static RegisterLicenseDialog newInstance() {
        RegisterLicenseDialog dlg = new RegisterLicenseDialog();
        return dlg;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return getActivity().getLayoutInflater().inflate(R.layout.register_license_dialog, container);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button ButtonOK = view.findViewById(R.id.bt_lic_ok);
        ButtonOK.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    String customerID = ((EditText)view.findViewById(R.id.edit_customer_id)).getText().toString();
                    String licenseID = ((EditText)view.findViewById(R.id.edit_license_id)).getText().toString();

                    if (customerID.isEmpty() || licenseID.isEmpty()){
                        Toast.makeText(getActivity(), "Please input both CustomerID and LicenseID", Toast.LENGTH_SHORT).show();
                    }
                    else {
                        String curCustomerID = Settings.getCustomerID(getContext());
                        String curLicenseID = Settings.getLicenseID(getContext());
                        if (!customerID.equals(curCustomerID) || !licenseID.equals(curLicenseID)){
                            Settings.setRequestNewLicense(getContext(), true);
                        }

                        LicenseInfo.getInstance().setLicenseInfo(getContext(), customerID, licenseID);

                        getActivity().startActivityForResult(mIntent, mRequestCode);

                        dismiss();
                    }
                }
            }
        );
        Button ButtonCancel = view.findViewById(R.id.bt_lic_cancel);
        ButtonCancel.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    //Close dialog
                    dismiss();
                }
            }
        );
    }

    public void setActivityAfterRegistered(Intent intent, int requestCode){
        mIntent = intent;
        mRequestCode = requestCode;
    }
}
