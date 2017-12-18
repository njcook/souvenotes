package com.souvenotes.souvenotes.note

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.souvenotes.souvenotes.R
import com.souvenotes.souvenotes.models.NoteListModel
import com.souvenotes.souvenotes.models.NoteModel

/**
 * Created on 10/23/17.
 */
class AddNotePresenter(private var addNoteView: IAddNotesContract.View?,
                       private val notesKey: String?) :
    IAddNotesContract.Presenter {

    private var existingNotesRef: DatabaseReference? = null
    private var valueEventListener: ValueEventListener? = null
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

    private var existingNote = NoteModel()

    override fun start() {
        if (userId == null) {
            addNoteView?.logout()
        }
        notesKey?.let {
            existingNotesRef = FirebaseDatabase.getInstance().reference.child("notes").child(
                userId).child(it)
            valueEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val note = snapshot.getValue(NoteModel::class.java)
                    if (note != null) {
                        existingNote = note
                        addNoteView?.onNoteLoaded(note)
                    } else {
                        addNoteView?.onLoadNoteError()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    addNoteView?.onLoadNoteError()
                }
            }
            existingNotesRef?.addValueEventListener(valueEventListener)
        }
    }

    override fun stop() {
        existingNotesRef?.removeEventListener(valueEventListener)
        addNoteView = null
    }

    override fun saveNote(title: String, content: String) {
        if ((existingNote.title != title || existingNote.content != content)) {
            val notesValues = NoteModel(title, content).toMap()
            val timestamp = System.currentTimeMillis()
            val noteListValues = NoteListModel(title, -1 * timestamp).toMap()
            val childUpdates = HashMap<String, Any>()

            val key = notesKey ?: FirebaseDatabase.getInstance().reference.child("notes").push().key
            childUpdates.put("/notes/$userId/$key", notesValues)
            childUpdates.put("/notes-list/$userId/$key", noteListValues)

            FirebaseDatabase.getInstance().reference.updateChildren(
                childUpdates).addOnFailureListener { exception ->
                Log.w("AddNotePresenter", exception.message ?: "", exception)
                if (notesKey != null) {
                    addNoteView?.onAddNoteError(R.string.add_note_error)
                } else {
                    addNoteView?.onAddNoteError(R.string.update_note_error)
                }
            }
        }
    }

    override fun deleteNote() {
        notesKey?.let {
            val childUpdates = HashMap<String, Any?>()
            childUpdates.put("/notes/$userId/$it", null)
            childUpdates.put("/notes-list/$userId/$it", null)
            //Prevent triggering error for successful delete
            FirebaseDatabase.getInstance().reference.updateChildren(childUpdates)
            existingNotesRef?.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(error: DatabaseError) {
                    existingNotesRef?.addValueEventListener(valueEventListener)
                    addNoteView?.onNoteDeleteError()
                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    addNoteView?.onNoteDeleted()
                }
            })
            existingNotesRef?.removeEventListener(valueEventListener)
        }
        if (notesKey == null) {
            addNoteView?.onNoteDeleted()
        }
    }
}