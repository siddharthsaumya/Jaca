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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.firestore.FirebaseFirestore;
import com.sidmya.jaca.databinding.ActivitySignUpBinding;
import com.sidmya.jaca.utilities.Constants;
import com.sidmya.jaca.utilities.PreferenceManager;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;

public class SignUpActivity extends AppCompatActivity {

    private String encodedImage;
    private ActivitySignUpBinding binding;
    private PreferenceManager preferenceManager;
    //public Boolean exists = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        setListeners();
    }
    private void setListeners(){
        binding.textSignIn.setOnClickListener(v -> onBackPressed());
        binding.buttonSignUp.setOnClickListener(v->{
            if(isValidSignUpDetails()){
                signUp();
            }
        });
        binding.layoutImage.setOnClickListener(v->{
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pickImage.launch(intent);
        });
    }

    private void showToast(String message){
        Toast.makeText(getApplicationContext(),message,Toast.LENGTH_SHORT).show();
    }

    private void signUp(){
        loading(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        HashMap<String, Object> user = new HashMap<>();
        user.put(Constants.KEY_NAME,binding.inputName.getText().toString());
        user.put(Constants.KEY_USERNAME,binding.inputUsername.getText().toString());
        user.put(Constants.KEY_PASSWORD,binding.inputPassword.getText().toString());
        user.put(Constants.KEY_IMAGE,encodedImage);
        database.collection(Constants.KEY_COLLECTION_USERS)
                .add(user)
                .addOnSuccessListener(documentReference -> {
                    loading(false);
                    preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN, true);
                    preferenceManager.putString(Constants.KEY_USER_ID, documentReference.getId());
                    preferenceManager.putString(Constants.KEY_USERNAME, binding.inputUsername.getText().toString().trim());
                    preferenceManager.putString(Constants.KEY_NAME, binding.inputName.getText().toString());
                    preferenceManager.putString(Constants.KEY_IMAGE, encodedImage);
                    Intent intent = new Intent(getApplicationContext(),MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .addOnFailureListener(exception->{
                    loading(false);
                    Log.e("ERROR","Error signing up because of: "+exception.getMessage());
                });
    }

    private String encodeImage(Bitmap bitmap){
        int previewWidth = 150;
        int previewHeight = bitmap.getHeight() * previewWidth/bitmap.getWidth();
        Bitmap previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight,false);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        previewBitmap.compress(Bitmap.CompressFormat.JPEG,50,byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(bytes,Base64.DEFAULT);
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
                        }catch (FileNotFoundException e){
                            e.printStackTrace();
                        }
                    }
                }
            }
    );
//
//    private void ifUsernameExists(String username){
//        FirebaseFirestore database = FirebaseFirestore.getInstance();
//        database.collectionGroup(Constants.KEY_COLLECTION_USERS).whereEqualTo(Constants.KEY_USERNAME, username).get()
//                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
//                    @Override
//                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
//                        for (QueryDocumentSnapshot snap : queryDocumentSnapshots) {
//                            if(snap.getString(Constants.KEY_USERNAME).equals(username)){
//                               try{
//                                   exists = true;
//                                   //return true;
//                               }catch (Exception e) {
//                                    Log.e("CHANGINGERROR", String.valueOf(e));
//                               }
//                            }
//                        }
//                    }
//                });
//        //return false;
//    }


    private Boolean isValidSignUpDetails(){
        //ifUsernameExists(binding.inputUsername.getText().toString().trim());
        if(encodedImage == null){
            encodedImage = "/9j/4AAQSkZJRgABAQAAAQABAAD/4gIoSUNDX1BST0ZJTEUAAQEAAAIYAAAAAAIQAABtbnRyUkdCIFhZWiAAAAAAAAAAAAAAAABhY3NwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAA9tYAAQAAAADTLQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAlkZXNjAAAA8AAAAHRyWFlaAAABZAAAABRnWFlaAAABeAAAABRiWFlaAAABjAAAABRyVFJDAAABoAAAAChnVFJDAAABoAAAAChiVFJDAAABoAAAACh3dHB0AAAByAAAABRjcHJ0AAAB3AAAADxtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAFgAAAAcAHMAUgBHAEIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFhZWiAAAAAAAABvogAAOPUAAAOQWFlaIAAAAAAAAGKZAAC3hQAAGNpYWVogAAAAAAAAJKAAAA+EAAC2z3BhcmEAAAAAAAQAAAACZmYAAPKnAAANWQAAE9AAAApbAAAAAAAAAABYWVogAAAAAAAA9tYAAQAAAADTLW1sdWMAAAAAAAAAAQAAAAxlblVTAAAAIAAAABwARwBvAG8AZwBsAGUAIABJAG4AYwAuACAAMgAwADEANv/bAEMAEAsMDgwKEA4NDhIREBMYKBoYFhYYMSMlHSg6Mz08OTM4N0BIXE5ARFdFNzhQbVFXX2JnaGc+TXF5cGR4XGVnY//bAEMBERISGBUYLxoaL2NCOEJjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY//AABEIAJYAlgMBIgACEQEDEQH/xAAaAAEAAwEBAQAAAAAAAAAAAAAAAQIEBQMG/8QALRABAAIBAwMDAgQHAAAAAAAAAAECAwQRIRIxQTJRcRMiFGFisTNCcoGRodH/xAAUAQEAAAAAAAAAAAAAAAAAAAAA/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAwDAQACEQMRAD8A+wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAeWXUY8W8TO9vaHlqtT0746erzPswg1TrckzxWsR+fKv4zLv/AC/4eADVTXW3++kTH6WrHlpk9Fonbu5aa2mlotWdpgHWHjp88ZqzxtaO8PYAAAAAAAAAAB558kYsU289o+Xoya+3FK+OZBj79wAAAAAWxXnHkraPHf4dSJi0RMTvE8xLkujpbdWnrv44B7AAAAAAiJ3S81ot7gsAAxa+PvpPvDaza6nVii0Rv0zz8AwgAAAAAh0dFG2nifeZc91MNejFSsxtMRz8guAACs29gTNthQAAAidlotHlUB6ExvG08w8+yeqQc/PinFeY56fE+7ydS8UyVmt44/Zz8uOtOa5K3j8p5BQQAlBu0YMNLWj6mSv9MTzILaPB1W+paPtjt+ctysTFY2iNojwibSC6s29lQCZ3AAAAAABW9646za08Am1orWZtO0R5Zsur8Y4/vLwzZrZbc8R4h5gm9739VpnzyqkAAAQkBembJT02nb2nmGrFqq24v9s/6YkA6ww6fUTTat+a/s3d43gAAAAAACZ2jee0Odnyzlv+mOzTrMk1pFI727/DECEgAAAAAAAACGrSZtp+naeJ7f8AGYB1RTDk+pii09/K4AAAAOdqbdWe3PEcPMmZmd57zzIAAAAAAAAAAAADTobz1Wp423bHP0szGort53iXQAAARb0z8ADlgAAAAAAAAAAAAA9NN/Hp8uiAAAP/2Q==";
        }
        if(binding.inputName.getText().toString().trim().isEmpty()){
            showToast("Enter name");
            return false;
        }else if(binding.inputUsername.getText().toString().trim().isEmpty()){
            showToast("Enter username");
            return false;
        }else if(binding.inputPassword.getText().toString().trim().isEmpty()){
            showToast("Enter password");
            return false;
        }else if(binding.inputConfirmPassword.getText().toString().trim().isEmpty()){
            showToast("Confirm your password");
            return false;
        }else if(!binding.inputPassword.getText().toString().equals(binding.inputConfirmPassword.getText().toString())){
            showToast("Password & confirm password must be same");
            return false;
        }else{
            return true;
        }
    }

    private void loading(Boolean isLoading){
        if(isLoading){
            binding.buttonSignUp.setVisibility(View.INVISIBLE);
            binding.progressBar.setVisibility(View.VISIBLE);
        }else{
            binding.buttonSignUp.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.INVISIBLE);
        }
    }

}