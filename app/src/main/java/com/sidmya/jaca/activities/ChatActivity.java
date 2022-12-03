package com.sidmya.jaca.activities;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.sidmya.jaca.adapters.ChatAdapter;
import com.sidmya.jaca.databinding.ActivityChatBinding;
import com.sidmya.jaca.models.ChatMessage;
import com.sidmya.jaca.models.User;
import com.sidmya.jaca.network.ApiClient;
import com.sidmya.jaca.network.ApiService;
import com.sidmya.jaca.utilities.Constants;
import com.sidmya.jaca.utilities.PreferenceManager;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends BaseActivity {

    private ActivityChatBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private String conversionId = null;
    private Boolean isReceiverAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        loadReceiverDetails();
        init();
        listenMessages();
    }

    private void init(){
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(
                chatMessages,
                getBitmapFromEncodedString(receiverUser.image),
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        binding.chatRecyclerView.setAdapter(chatAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void sendMessage() {
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());
        message.put(Constants.KEY_TIMESTAMP, new Date());
        database.collection(Constants.KEY_COLLECTION_CHAT).add(message);
        if(conversionId != null){
            updateConversion(binding.inputMessage.getText().toString());
        }else{
            HashMap<String, Object> conversion = new HashMap<>();
            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
            conversion.put(Constants.KEY_LAST_MESSAGE, binding.inputMessage.getText().toString());
            conversion.put(Constants.KEY_TIMESTAMP, new Date());
            addConversion(conversion);
        }
        if(!isReceiverAvailable){
            try{
                JSONArray tokens = new JSONArray();
                tokens.put(receiverUser.token);

                JSONObject data = new JSONObject();
                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                data.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());

                JSONObject body = new JSONObject();
                body.put(Constants.REMOTE_MSG_DATA, data);
                body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

                sendNotification(body.toString());
            }catch (Exception exception){
                showToast(exception.getMessage());
            }
        }
        binding.inputMessage.setText(null);
    }

    private void showToast(String message){
        Toast.makeText(getApplicationContext(),message,Toast.LENGTH_SHORT).show();
    }

    private void sendNotification(String messageBody){
        ApiClient.getClient().create(ApiService.class).sendMessage(
                Constants.getRemoteMsgHeaders(),
                messageBody
        ).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if(response.isSuccessful()){
                    try{
                        if(response.body() != null){
                            JSONObject responseJson = new JSONObject(response.body());
                            JSONArray results = responseJson.getJSONArray("results");
                            if(responseJson.getInt("failure") == 1){
                                JSONObject error = (JSONObject) results.get(0);
                                //showToast(error.getString("error"));
                                return;
                            }
                        }
                    }catch (JSONException e){
                        e.printStackTrace();
                    }
                    //showToast("Notification sent successfully");
                }else{
                    //showToast("Error: "+response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                //showToast(t.getMessage());
            }
        });
    }

    private void listenAvailabilityOfReceiver(){
        database.collection(Constants.KEY_COLLECTION_USERS).document(
                receiverUser.id
        ).addSnapshotListener(ChatActivity.this, (value, error) -> {
            if(error != null){
                return;
            }
            if(value != null){
                if(value.getLong(Constants.KEY_AVAILABILITY) != null){
                    int availability = Objects.requireNonNull(
                            value.getLong(Constants.KEY_AVAILABILITY)
                    ).intValue();
                    isReceiverAvailable = availability == 1;
                }
                receiverUser.token = value.getString(Constants.KEY_FCM_TOKEN);
                if(receiverUser.image == null){
                    receiverUser.image = value.getString(Constants.KEY_IMAGE);
                    chatAdapter.setReceiverProfileImage(getBitmapFromEncodedString(receiverUser.image));
                    chatAdapter.notifyItemRangeChanged(0, chatMessages.size());
                }
            }
            if(isReceiverAvailable){
                binding.textAvailability.setVisibility(View.VISIBLE);
            }else{
                binding.textAvailability.setVisibility(View.GONE);
            }
        });
    }

    private void listenMessages(){
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID,receiverUser.id)
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if(error != null){
            return;
        }
        if (value != null){
            int count = chatMessages.size();
            for(DocumentChange documentChange : value.getDocumentChanges()){
                if(documentChange.getType() == DocumentChange.Type.ADDED){
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    chatMessage.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    chatMessage.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                    chatMessage.dateTime = getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                    chatMessages.add(chatMessage);
                }
            }
            Collections.sort(chatMessages, (obj1, obj2) -> obj1.dateObject.compareTo(obj2.dateObject));
            if(count == 0){
                chatAdapter.notifyDataSetChanged();
            }else{
                chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }
            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }
        binding.progressBar.setVisibility(View.GONE);
        if(conversionId == null){
            checkForConversion();
        }
    };

    private Bitmap getBitmapFromEncodedString(String encodedImage){
        if(encodedImage != null){
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }else{
            return null;
        }
    }

    private void loadReceiverDetails(){
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textName.setText(receiverUser.name);
    }



    private void getAutoReply(){
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference docRef = database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversionId);
        docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        //Log.d("TAG", "DocumentSnapshot data: " + document.getString(Constants.KEY_LAST_MESSAGE));
                        binding.inputMessage.setText(generateAutoReply(document.getString(Constants.KEY_LAST_MESSAGE)));
                        binding.inputMessage.setSelection(binding.inputMessage.getText().length());
                    } else {
                        Log.e("ERROR MESSAGE", "No Last message found");
                    }
                } else {
                    Log.e("ERROR MESSAGE", "get failed with ", task.getException());
                }
            }
        });
    }

    private void deleteChats(){
            deleteSenderChat();
            deleteReceiverChat();
            deleteConversation();
            showToast("Chat deleted successfully");
    }

    private void deleteConversation(){
            FirebaseFirestore.getInstance()
                .collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .document(conversionId)
                    .delete()
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            Log.d("DELETE","Deleted conversation successfully");
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull @NotNull Exception e) {
                            Log.e("DELETE","Deleting conversation unsuccessful");
                        }
                    });
    }

    private void deleteSenderChat(){
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_CHAT).whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id).
                get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        WriteBatch batch = FirebaseFirestore.getInstance().batch();
                        List<DocumentSnapshot> snapshotList = queryDocumentSnapshots.getDocuments();
                        for(DocumentSnapshot snapshot: snapshotList){
                            batch.delete(snapshot.getReference());
                        }
                        batch.commit()
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        Log.i("DELETED","Deleted all sender chats");
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull @NotNull Exception e) {
                                        Log.e("DELETED","Couldn't delete sender chats because: "+e);
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull @NotNull Exception e) {
                        Log.e("DELETED","Couldn't delete sender chats because: "+e);
                    }
                });
    }

    private void deleteReceiverChat(){
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_CHAT).whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id).
                get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        WriteBatch batch = FirebaseFirestore.getInstance().batch();
                        List<DocumentSnapshot> snapshotList = queryDocumentSnapshots.getDocuments();
                        for(DocumentSnapshot snapshot: snapshotList){
                            batch.delete(snapshot.getReference());
                        }
                        batch.commit()
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        Log.i("DELETED","Deleted all receiver chats");
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull @NotNull Exception e) {
                                        Log.i("DELETED","Couldn't delete receiver chats because: "+e);
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull @NotNull Exception e) {
                        Log.i("DELETED","Couldn't delete receiver chats because: "+e);
                    }
                });
    }



    private void setListeners(){
        binding.imageBack.setOnClickListener(v->onBackPressed());
        binding.layoutSend.setOnClickListener(v->sendMessage());
        binding.layoutAutoReply.setOnClickListener(v->getAutoReply());
        binding.imageDelete.setOnClickListener(v->deleteChats());
    }

    private String getReadableDateTime(Date date){
        return new SimpleDateFormat("dd MMM yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    private void addConversion(HashMap<String, Object> conversion){
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId());
    }

    private void updateConversion(String message){
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversionId);
        documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_TIMESTAMP, new Date()
        );
    }

    private void checkForConversion(){
        if(chatMessages.size() != 0){
            checkForConversionRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),
                    receiverUser.id
            );
            checkForConversionRemotely(
                    receiverUser.id,
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }

    private void checkForConversionRemotely(String senderId, String receiverId){
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID,senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversionOnCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversionOnCompleteListener = task -> {
        if(task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0){
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversionId = documentSnapshot.getId();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }

    private String generateAutoReply(String input){
        String[] questions ={"Hello", "How are you?", "Hi there", "Hi", "Whats up", "how old are you?", "when is your birthday?", "when was you born?", "what are you doing this weekend?", "do you want to hang out some time?", "what are your plans for this week", "what's your name?", "what are you called?", "who are you?", "What do people call you?", "bye", "g2g", "see ya", "adios", "cya", "How are you feeling today", "Is everything good?", "How is your mood today", "How's your mood lately?", "Do you want something?", "Is it a necessity?", "Anything you want?", "Congratulations on your sucess.", "May you achieve greater heights of success", "You achieved it all and more to come", "May you come out with flying colors", "Congratulations, many more to come", "Where do you live?", "For how long have you been staying?", "What is your location?", "For how long have you been shifted?", "Can you send me your exact location?", "Where are you from?", "Who is you favourite person?", "What is you favourite animal/bird?", "What is you favourite dish?", "Who is your favourite character?", "What is your favourite book to read?", "What is your favourite place?", "Who is your best friend?", "What do you prefer doing the most?", "How do you love to spend your time?", "Where are you working?", "Do your skills align with the work?", "How are you feeling working at your workplace?", "Is your work life balance good?", "Can you recommend me anything related to jobs?", "How can we grow at the workplace?", "Can you describe your job and workplace", "Thank you!", "Thank you so much", "That's great", "That's so sweet of you", "Thanks for the ABC...", "What is the latest technology", "Tell me something related to Tech field", "Technology trends", "Wish you a very Happy Diwali/Holi/New Year/Christmas/Eid", "Lots of love and propsperity", "Have a wonderful year ahead!", "What will you eat?", "Are you eating something?", "Any food suggestions?", "What is your favourite go to dish?", "What's your favourite thing to eat?", "Would you like to order something?", "Have some tea/coffee/noodles.", "Feeling hungry", "Who is your friend/bestfriend?", "Is ABC you good friend?", "He/She seems to be your very good friend", "It's good to have amazing friends", "We are deciding to go out with friends", "Would you be my friend?", "A friend in need is a friend in deed.", "One good friend is worth many friends", "Will you go out with my friends?", "Where do you study?", "In which class do you study?", "What is your educational status?", "In which school/college do you study?", "In which city/state your school/college located?"};
        String[] answers = {"Hello", "How are you doing?", "Greetings!", "How do you do?", "I am 22 years old", "I was born in 2000", "My birthday is Jan 23 and I was born in 2000", "23/01/2000", "I am available all week", "I don't have any plans", "I am not busy", "My name is ABC", "I'm ABC", "ABC", "It was nice speaking to you", "See you later", "Speak soon!", "I am feeling alright", "I am all good, How are you?", "I am good", "I am not well though", "Yes, I want it", "Yes, much needed", "No I don't want it.", "Thanks for asking but I don't need", "Thank you", "Thank you so much", "Thanks, you are amazing!", "Thanks, you too!", "I stay in ABC colony.", "I have been staying here for 6 years", "I have been shifting frequently", "I am from India", "I stay in ABC colony, How about you?", "My favourite person is XYZ", "My favourite animal/bird is Dog/Peacock", "My favourite dish is Italian", "My favourite character is Mr. Bean", "My favourite book is ABC", "My favourite place is India", "My best friend is ABC", "I prefer doing XYZ", "I spend my time by ABC", "How about your favourites?", "I am currently working at ABC Corporation", "I am not working anywhere", "I am a student", "I am looking for a job", "My skills are ABC,XYZ,PQR", "My skillset match my job role", "Yes,maybe", "No", "I can recommend you to ABC...", "We can grow in a variety of ways", "My job is amazing", "I learn a lot at my workplace", "Welcome!", "You're welcome", "My pleasure", "No problem", "Technology is evolving at a faster rate", "Big Data", "Artificial Intelligence", "Blockchain", "Machine Learning", "Cloud Computing", "If you also know anything related to Tech then you can say", "Thank you, wishing you the same", "You too!", "Thanks!", "Thank you so much", "Thanks a lot!", "Lots of love", "I would like to have ABC/XYZ", "No, thanks", "Yes,I am hungry", "Feeling hungry", "There is a good restaurant near ABC location", "ABC/XYZ tastes nice", "My go to dish is ABC/XYZ", "Let's order somethng!", "My best friend is ABC/XYZ", "ABC/XYZ is my go to person", "Yes, he/she is amazing", "Yup!", "Indeed", "Yup, I am ready", "Let's go!", "True", "I study in ABC/XYZ", "I study in class 1..10/1..8 Semester", "My educational status is ...", "I study in ABC college/university", "My school/college is located in ABC/XYZ"};
        for(int i=0;i<questions.length;i++){
            if(input.trim().toLowerCase().equals(questions[i].trim().toLowerCase())){
                return answers[i];
            }
        }
        return "😊";
    }
}