package com.example.chatapp.datas.repositories

import android.net.Uri
import android.util.Log
import com.example.chatapp.datas.models.BoxChat
import com.example.chatapp.datas.models.Message
import com.example.chatapp.datas.models.User
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class BoxChatRepositoryImpl : BoxChatRepository {
    private val db = Firebase.database
    private val storage = Firebase.storage

    private val avatarRef = storage.getReference("UserAvatar")
    private val boxAvatar = storage.getReference("BoxAvatar")

    private val boxRef = db.getReference("boxChats")
    private val userRef = db.getReference("users")


    override fun createBoxChat(userId: String, boxName: String): StateFlow<Boolean?> {
        val createBoxStatus = MutableStateFlow<Boolean?>(null)
        avatarRef.child("default.jpg").downloadUrl.addOnSuccessListener {
            val defaultAvatar = it.toString()
            val newBoxId = boxRef.push().key!!
            var newBoxChat = BoxChat(newBoxId, boxName, defaultAvatar, "Box Chat Created")

            boxRef.child(newBoxId).setValue(newBoxChat).addOnSuccessListener {

                userRef.child(userId).child("boxIdList").child(newBoxId).setValue(true).addOnSuccessListener {
                    createBoxStatus.value = true
                }.addOnFailureListener {
                    createBoxStatus.value = false
                }
            }.addOnFailureListener {
                createBoxStatus.value = false
            }
        }.addOnFailureListener {
            createBoxStatus.value = false
        }.addOnCanceledListener {
            createBoxStatus.value = false
        }
        return createBoxStatus
    }

    override fun getBoxList(boxId: List<String>): MutableStateFlow<List<BoxChat>> {
        val boxList = MutableStateFlow(emptyList<BoxChat>())
        boxRef.addValueEventListener(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newBox = mutableListOf<BoxChat>()
                snapshot.children.forEach {boxItem ->
                    if (boxId.contains(boxItem.key)) {
                        val boxId = boxItem.key.toString()
                        val boxName = boxItem.child("name").value.toString()
                        val boxAvatar = boxItem.child("avatar").value.toString()
                        var boxMess = boxItem.child("lastMess").value.toString()
                        if (boxMess.isNullOrEmpty()) {
                            boxMess = ""
                        }

                        val newItem = BoxChat(boxId, boxName, boxAvatar, boxMess)

                        newBox.add(newItem)
                    }
                }

                boxList.update { newBox }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("ChatApp", "getBox Cancelled")
            }

        })
        return boxList
    }

    override fun getBoxInfo(boxId: String): MutableStateFlow<List<String>> {
        val boxInfo = MutableStateFlow(emptyList<String>())

        boxRef.child(boxId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userName = snapshot.child("name").value.toString()
                val avatarUrl = snapshot.child("avatar").value.toString()

                val newList = listOf(userName, avatarUrl)

                boxInfo.update { newList }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("ChatApp", "getBoxInfo Cancelled")
            }
        })
        return boxInfo
    }

    override fun sendMess(userId: String, boxId: String, data: String) {
        val newMess = Message(userId, data)

        val messKey = boxRef.child(boxId).child("messages").push().key!!
        boxRef.child(boxId).child("messages").child(messKey)
            .setValue(newMess)
            .addOnFailureListener {
                Log.d("sendMess", "False")
            }
        boxRef.child(boxId).child("lastMess")
            .setValue(data)
            .addOnFailureListener {
                Log.d("ChatApp", "set lastMess Failure")
            }
    }

    override fun getMess(userId: String, boxId: String): MutableStateFlow<List<Message>> {
        val messList = MutableStateFlow(emptyList<Message>())
        boxRef.child(boxId).child("messages").addValueEventListener( object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var newList = mutableListOf<Message>()
                snapshot.children.forEach {
                    val sender = it.child("sender").value.toString()
                    val data = it.child("data").value.toString()

                    val newMess = Message(sender, data)
                    newList.add(newMess)
                }
                messList.update { newList }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("ChatApp", "getMess Cancelled")
            }

        })
        return messList
    }

    override fun setImage(boxId: String, image: Uri): MutableStateFlow<Boolean?> {
        val uploadState = MutableStateFlow<Boolean?>(null)
        val boxAvatar = boxAvatar.child("$boxId.jpg")
        boxAvatar.putFile(image).addOnSuccessListener {
            it.storage.downloadUrl.addOnSuccessListener { uri ->
                setBoxAvatarUrl(boxId, uri)
                uploadState.value = true
            }
        }.addOnFailureListener {
            uploadState.value = false
        }.addOnCanceledListener {
            uploadState.value = false
        }

        return uploadState
    }

    override fun setBoxAvatarUrl(boxId: String, image: Uri) {
        boxRef.child(boxId).child("avatar").setValue(image.toString())
    }

    override fun setName(boxId: String, name: String): MutableStateFlow<Boolean?> {
        val updateState = MutableStateFlow<Boolean?>(null)

        boxRef.child(boxId).child("name").setValue(name).addOnSuccessListener {
            updateState.value = true
        }.addOnFailureListener {
            updateState.value = false
        }.addOnCanceledListener {
            updateState.value = false
        }
        return updateState
    }

    override fun getUsetList(boxId: String): MutableStateFlow<List<User>> {
        TODO("Not yet implemented")
    }

    override fun addUser(boxId: String, userId: String): MutableStateFlow<Boolean?> {
        val addState = MutableStateFlow<Boolean?>(null)
        userRef.child(userId).child("boxIdList").child(boxId).setValue(true).addOnCanceledListener {
            addState.value = true
        }.addOnFailureListener {
            addState.value = false
        }.addOnCanceledListener {
            addState.value = false
        }
        return addState
    }
}