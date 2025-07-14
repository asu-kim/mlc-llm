import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.*

object CalendarUtils {

    fun fetchAllCalendarEvents(context: Context): List<String> {
        val events = mutableListOf<String>()

        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND // <-- Add end time!
        )

        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        val cursor: Cursor? = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
            val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
//            val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)

            while (it.moveToNext()) {
                val title = it.getString(titleIdx)
                val startTimeMillis = it.getLong(startIdx)
//                val endTimeMillis = it.getLong(endIdx)

                val timeFormatter = SimpleDateFormat("EEE, MMM d yyyy h:mm a", Locale.getDefault())
                val startTimeStr = timeFormatter.format(Date(startTimeMillis))
//                val endTimeStr = timeFormatter.format(Date(endTimeMillis))

                // You can change this display as you wish:
//                events.add("$title at $startTimeStr – $endTimeStr")
                events.add("$title at $startTimeStr")
            }
        }

        return events
    }
}