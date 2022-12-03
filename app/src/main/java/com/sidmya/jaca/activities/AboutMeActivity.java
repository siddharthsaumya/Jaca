package com.sidmya.jaca.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.firebase.firestore.FirebaseFirestore;
import com.sidmya.jaca.databinding.ActivityAboutMeBinding;
import com.sidmya.jaca.utilities.Constants;
import com.sidmya.jaca.utilities.PreferenceManager;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class AboutMeActivity extends BaseActivity{

    private String encodedImage = null;
    private ActivityAboutMeBinding binding;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAboutMeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        setDefaultValues();
        setListeners();
    }

    private void loading(Boolean isLoading){
        if(isLoading){
            binding.buttonSave.setVisibility(View.INVISIBLE);
            binding.progressBar.setVisibility(View.VISIBLE);
        }else{
            binding.buttonSave.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.INVISIBLE);
        }
    }

    private void setDefaultValues(){
        binding.inputName.setText(preferenceManager.getString(Constants.KEY_NAME));
        binding.inputUsername.setText(preferenceManager.getString(Constants.KEY_USERNAME));
    }

    private void setListeners(){
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.buttonSave.setOnClickListener(v->{
            if(isCredentialsUpdated()){
                update();
            }
        });
        binding.layoutImage.setOnClickListener(v->{
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pickImage.launch(intent);
        });
    }

    ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if(result.getResultCode() == RESULT_OK){
                    if(result.getData() != null){
                        Uri imageUri = result.getData().getData();
                        try{
                            InputStream inputStream = getContentResolver().openInputStream(imageUri);
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            binding.imageProfile.setImageBitmap(bitmap);
                            binding.textAddImage.setVisibility(View.GONE);
                            encodedImage = encodeImage(bitmap);
                            Log.i("IMAGE",encodedImage);
                        }catch (FileNotFoundException e){
                            e.printStackTrace();
                        }
                    }
                }
            }
    );

    private String encodeImage(Bitmap bitmap){
        int previewWidth = 150;
        int previewHeight = bitmap.getHeight() * previewWidth/bitmap.getWidth();
        Bitmap previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight,false);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        previewBitmap.compress(Bitmap.CompressFormat.JPEG,50,byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(bytes,Base64.DEFAULT);
    }

    private void showToast(String message){
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private Boolean isCredentialsUpdated(){
        if(encodedImage == null){
            encodedImage = preferenceManager.getString(Constants.KEY_IMAGE);
        }
        if(binding.inputUsername.getText().toString().trim().isEmpty()){
            showToast("Enter new username");
            return false;
        }else if(binding.inputUsername.getText().toString().trim().equals(preferenceManager.getString(Constants.KEY_USERNAME)) && binding.inputName.getText().toString().trim().equals(preferenceManager.getString(Constants.KEY_NAME)) && encodedImage.equals(preferenceManager.getString(Constants.KEY_IMAGE))){
            showToast("Change username or name to save");
            return false;
        }else if(binding.inputName.getText().toString().trim().isEmpty()){
            showToast("Enter new name");
            return false;
        }else{
            return true;
        }
    }

    private void update(){
        String username,name;
        username = binding.inputUsername.getText().toString();
        name = binding.inputName.getText().toString();
        loading(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_USERS)
                .document(preferenceManager.getString(Constants.KEY_USER_ID))
                .update(
                Constants.KEY_NAME, name,
                        Constants.KEY_USERNAME, username,
                        Constants.KEY_IMAGE,encodedImage
                )
                .addOnCompleteListener(task -> {
                    if(task.isSuccessful()){
                        loading(false);
                        showToast("Credentials updated");
                        preferenceManager.putString(Constants.KEY_NAME, name);
                        preferenceManager.putString(Constants.KEY_USERNAME, username);
                        preferenceManager.putString(Constants.KEY_IMAGE, encodedImage);
                    }else{
                        loading(false);
                        showToast("Unable to sign in");
                    }
                });
    }

}