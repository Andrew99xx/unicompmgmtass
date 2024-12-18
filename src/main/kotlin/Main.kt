import androidx.compose.foundation.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.material.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.sql.Connection
import java.sql.DriverManager
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.window.singleWindowApplication
import java.sql.SQLException
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.singleWindowApplication
fun main() = singleWindowApplication {
    DatabaseHelper.initializeDatabase() // Initialize the database
//    addSampleRooms()

    MaterialTheme {
        App()
    }
}

fun addSampleRooms() {
    val sql = "INSERT INTO rooms (building, os_type) VALUES (?, ?)"

    listOf(
        Pair("BuildingA", "Windows"),
        Pair("BuildingB", "Mac"),
        Pair("BuildingC", "Linux"),
        Pair("BuildingA", "Linux"),
        Pair("BuildingB", "Windows")
    ).forEach { (building, os) ->
        DatabaseHelper.getConnection()?.use { connection ->
            connection.prepareStatement(sql).use { preparedStatement ->
                preparedStatement.setString(1, building)
                preparedStatement.setString(2, os)
                preparedStatement.executeUpdate()
            }
        }
    }
}

fun addRoomToDatabase(building: String, osType: String, computerCount: Int): Boolean {
    val sql = "INSERT INTO rooms (building, os_type, computer_count) VALUES (?, ?, ?)"

    return try {
        DatabaseHelper.getConnection()?.use { connection ->
            connection.prepareStatement(sql).use { preparedStatement ->
                preparedStatement.setString(1, building)
                preparedStatement.setString(2, osType)
                preparedStatement.setInt(3, computerCount)
                preparedStatement.executeUpdate()
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

object DatabaseHelper {
    private const val DB_URL = "jdbc:sqlite:room_booking.db"

    fun getConnection(): Connection? {
        return try {
            DriverManager.getConnection(DB_URL)
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        }
    }
    fun initializeDatabase() {
        val sqlDropBookings = "DROP TABLE IF EXISTS bookings;"
        val sqlDropComputers = "DROP TABLE IF EXISTS computers;"
        val sqlDropRooms = "DROP TABLE IF EXISTS rooms;"
        val sqlDropUsers = "DROP TABLE IF EXISTS users;"

        val sqlRooms = """
        CREATE TABLE IF NOT EXISTS rooms (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            building TEXT NOT NULL,
            os_type TEXT NOT NULL,
            computer_count INTEGER NOT NULL
        );
    """.trimIndent()

        val sqlUsers = """
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password TEXT NOT NULL,
            role TEXT NOT NULL
        );
    """.trimIndent()

        val sqlComputers = """
        CREATE TABLE IF NOT EXISTS computers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            room_id INTEGER NOT NULL,
            computer_id INTEGER NOT NULL,
            os_type TEXT NOT NULL,
            FOREIGN KEY (room_id) REFERENCES rooms(id)
        );
    """.trimIndent()

        val sqlBookings = """
        CREATE TABLE IF NOT EXISTS bookings (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            room TEXT NOT NULL,
            computer_id TEXT NOT NULL,
            booking_id TEXT UNIQUE NOT NULL,
            day TEXT NOT NULL,
            timeslot TEXT NOT NULL,
            username TEXT NOT NULL
        );
    """.trimIndent()

        getConnection()?.use { connection ->
            connection.createStatement().use { statement ->
                // Drop tables if they exist
//                statement.execute(sqlDropBookings)
//                statement.execute(sqlDropComputers)
//                statement.execute(sqlDropRooms)
//                statement.execute(sqlDropUsers)

                // Create new tables
                statement.execute(sqlRooms)
                statement.execute(sqlUsers)
                statement.execute(sqlComputers)
                statement.execute(sqlBookings)
            }
        }
    }

    fun updateRoomOSType(room: String, newOSType: String): Boolean {
        val sql = "UPDATE rooms SET os_type = ? WHERE building = ?"

        return try {
            DatabaseHelper.getConnection()?.use { connection ->
                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, newOSType)
                    stmt.setString(2, room)
                    stmt.executeUpdate() > 0
                }
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    fun fetchRoomComputerStatus(room: String, day: String, timeslot: String): List<Map<String, String>> {
        val computers = mutableListOf<Map<String, String>>()
        val sql = """
        SELECT 
            c.computer_id,
            c.os_type,
            CASE
                WHEN EXISTS (
                    SELECT 1
                    FROM bookings b
                    WHERE b.computer_id = c.computer_id
                      AND b.room = ?
                      AND b.day = ?
                      AND b.timeslot = ?
                ) THEN
                    (SELECT 
                        CASE
                            WHEN b.username = ? THEN 'user'
                            ELSE 'reserved'
                        END
                    FROM bookings b
                    WHERE b.computer_id = c.computer_id
                      AND b.room = ?
                      AND b.day = ?
                      AND b.timeslot = ?
                    LIMIT 1)
                ELSE 'free'
            END AS status
        FROM computers c
        WHERE c.room_id = (SELECT id FROM rooms WHERE building = ?)
    """.trimIndent()

        getConnection()?.use { connection ->
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, room)          // For the first subquery
                stmt.setString(2, day)
                stmt.setString(3, timeslot)
                stmt.setString(4, "student123")  // Replace with the logged-in username
                stmt.setString(5, room)          // For the CASE logic
                stmt.setString(6, day)
                stmt.setString(7, timeslot)
                stmt.setString(8, room)          // For the final room selection

                val resultSet = stmt.executeQuery()
                while (resultSet.next()) {
                    computers.add(
                        mapOf(
                            "id" to resultSet.getString("computer_id"),
                            "os_type" to resultSet.getString("os_type"),
                            "status" to resultSet.getString("status")
                        )
                    )
                }
            }
        }
        return computers
    }


    fun fetchBookingsForRoomAndDay(room: String, day: String): List<Map<String, String>> {
        val bookings = mutableListOf<Map<String, String>>()
        val sql = """
        SELECT booking_id, computer_id, day, timeslot, username 
        FROM bookings 
        WHERE room = ? AND day = ?;
    """.trimIndent()

        getConnection()?.use { connection ->
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, room)
                stmt.setString(2, day)

                val resultSet = stmt.executeQuery()
                while (resultSet.next()) {
                    bookings.add(
                        mapOf(
                            "booking_id" to resultSet.getString("booking_id"),
                            "computer_id" to resultSet.getString("computer_id"),
                            "day" to resultSet.getString("day"),
                            "timeslot" to resultSet.getString("timeslot"),
                            "username" to resultSet.getString("username")
                        )
                    )
                }
            }
        }
        println("Bookings----"+ bookings)
        return bookings
    }

    fun fetchRooms(): List<String> {
        val rooms = mutableListOf<String>()
        val sql = "SELECT DISTINCT building FROM rooms"

        getConnection()?.use { connection ->
            connection.createStatement().use { statement ->
                val resultSet = statement.executeQuery(sql)
                while (resultSet.next()) {
                    rooms.add(resultSet.getString("building"))
                }
            }
        }
        return rooms
    }

    fun fetchAvailableComputers(room: String, os: String): List<String> {
        val computers = mutableListOf<String>()
        val sql = """
        SELECT c.computer_id
        FROM computers c
        JOIN rooms r ON c.room_id = r.id
        WHERE r.building = ? AND c.os_type = ?
        EXCEPT 
        SELECT computer_id 
        FROM bookings 
        WHERE room = ? AND day = ? AND timeslot = ?
    """.trimIndent()

        getConnection()?.use { connection ->
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, room)
                stmt.setString(2, os)
                stmt.setString(3, room)
                stmt.setString(4, "") // Default placeholder day for now
                stmt.setString(5, "") // Default placeholder timeslot for now

                val resultSet = stmt.executeQuery()
                while (resultSet.next()) {
                    computers.add(resultSet.getString("computer_id"))
                }
            }
        }
        return computers
    }

    fun bookComputer(room: String, computer: String, day: String, timeslot: String, username: String): String {
        val checkSql = """
            SELECT COUNT(*) AS count FROM bookings
            WHERE room = ? AND computer_id = ? AND day = ? AND timeslot = ?
        """.trimIndent()

        val insertSql = """
            INSERT INTO bookings (room, computer_id, booking_id, day, timeslot, username)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            getConnection()?.use { connection ->
                // Check for duplicate booking
                connection.prepareStatement(checkSql).use { stmt ->
                    stmt.setString(1, room)
                    stmt.setString(2, computer)
                    stmt.setString(3, day)
                    stmt.setString(4, timeslot)

                    val resultSet = stmt.executeQuery()
                    if (resultSet.next() && resultSet.getInt("count") > 0) {
                        return "Booking failed: Computer $computer in Room $room is already booked for $day at $timeslot."
                    }
                }

                // Insert new booking
                val bookingId = "$room-$computer"
                connection.prepareStatement(insertSql).use { stmt ->
                    stmt.setString(1, room)
                    stmt.setString(2, computer)
                    stmt.setString(3, bookingId)
                    stmt.setString(4, day)
                    stmt.setString(5, timeslot)
                    stmt.setString(6, username)
                    stmt.executeUpdate()
                }

                "Booking successful: Booking ID $bookingId, Room $room, Computer $computer, $day, $timeslot"
            } ?: "Database connection failed."
        } catch (e: Exception) {
            e.printStackTrace()
            "Booking failed. Please try again."
        }
    }

    fun fetchComputersInRoom(room: String): List<String> {
        val computers = mutableListOf<String>()
        val sql = """
        SELECT computer_id
        FROM computers
        WHERE room_id = (SELECT id FROM rooms WHERE building = ?)
    """.trimIndent()

        getConnection()?.use { connection ->
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, room)
                val resultSet = stmt.executeQuery()
                while (resultSet.next()) {
                    computers.add(resultSet.getString("computer_id"))
                }
            }
        }
        return computers
    }

    fun updateComputerOSType(room: String, computerId: String, newOSType: String): Boolean {
        val sql = """
        UPDATE computers
        SET os_type = ?
        WHERE computer_id = ? AND room_id = (SELECT id FROM rooms WHERE building = ?)
    """.trimIndent()

        return try {
            getConnection()?.use { connection ->
                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, newOSType)
                    stmt.setString(2, computerId)
                    stmt.setString(3, room)
                    stmt.executeUpdate() > 0
                }
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun fetchBookingsForUser(username: String): List<Map<String, String>> {
        val bookings = mutableListOf<Map<String, String>>()
        val sql = """
        SELECT booking_id, room, computer_id, day, timeslot 
        FROM bookings 
        WHERE username = ?;
    """.trimIndent()

        getConnection()?.use { connection ->
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username)
                val resultSet = stmt.executeQuery()
                while (resultSet.next()) {
                    bookings.add(
                        mapOf(
                            "booking_id" to resultSet.getString("booking_id"),
                            "room" to resultSet.getString("room"),
                            "computer_id" to resultSet.getString("computer_id"),
                            "day" to resultSet.getString("day"),
                            "timeslot" to resultSet.getString("timeslot")
                        )
                    )
                }
            }
        }
        return bookings
    }
    fun cancelBooking(bookingId: String): Boolean {
        val sql = "DELETE FROM bookings WHERE booking_id = ?"

        return try {
            getConnection()?.use { connection ->
                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, bookingId)
                    stmt.executeUpdate() > 0
                }
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }




    fun searchRooms(building: String, osType: String): List<String> {
        val rooms = mutableListOf<String>()
        val sql = "SELECT * FROM rooms WHERE building = ? AND os_type = ?"

        getConnection()?.use { connection ->
            connection.prepareStatement(sql).use { preparedStatement ->
                preparedStatement.setString(1, building)
                preparedStatement.setString(2, osType)

                val resultSet = preparedStatement.executeQuery()
                while (resultSet.next()) {
                    val room = "Room ID: ${resultSet.getInt("id")}, Building: ${resultSet.getString("building")}, OS: ${resultSet.getString("os_type")}"
                    rooms.add(room)
                }
            }
        }

        return rooms
    }
}



@Composable
fun SignupScreen(onSignupSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("STUDENT") } // Default role as STUDENT
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row {
            Text("Role: ")
            Spacer(modifier = Modifier.width(8.dp))
            DropdownMenuOptions(role, onRoleChange = { role = it })
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (signupUser(username, password, role)) {
                message = "Signup successful!"
                onSignupSuccess()
            } else {
                message = "Signup failed. Username may already exist."
            }
        }) {
            Text("Sign Up")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(message)
    }
}

@Composable
fun DropdownMenuOptions(selectedRole: String, onRoleChange: (String) -> Unit) {
    val options = listOf("STUDENT", "ADMIN")
    var expanded by remember { mutableStateOf(false) }

    Box {
        Text(selectedRole, modifier = Modifier.clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { role ->
                DropdownMenuItem(onClick = {
                    onRoleChange(role)
                    expanded = false
                }) {
                    Text(role)
                }
            }
        }
    }
}


fun signupUser(username: String, password: String, role: String): Boolean {
    val sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)"

    return try {
        DatabaseHelper.getConnection()?.use { connection ->
            connection.prepareStatement(sql).use { preparedStatement ->
                preparedStatement.setString(1, username)
                preparedStatement.setString(2, password)
                preparedStatement.setString(3, role)
                preparedStatement.executeUpdate()
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
enum class UserType {
    STUDENT, ADMIN
}

@Composable
fun App() {
    val (userType, setUserType) = remember { mutableStateOf<UserType?>(null) }
    var showSignup by remember { mutableStateOf(false) }

    when {
        showSignup -> SignupScreen(onSignupSuccess = { showSignup = false })
        userType == null -> {
            loginScreen(
                onLogin = setUserType,
                onSignup = { showSignup = true }
            )
        }
        userType == UserType.STUDENT -> StudentScreen(onLogout = { setUserType(null) })
        userType == UserType.ADMIN -> AdminScreen(onLogout = { setUserType(null) })
    }
}


@Composable
fun loginScreen(onLogin: (UserType) -> Unit, onSignup: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val userType = authenticate(username, password)
            if (userType != null) {
                onLogin(userType)
            }
        }) {
            Text("Login")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = { onSignup() }) {
            Text("Sign Up")
        }
    }
}
fun authenticate(username: String, password: String): UserType? {
    // Replace this with actual SQLite logic
    return when {
        username == "admin" && password == "admin" -> UserType.ADMIN
        username.startsWith("student") -> UserType.STUDENT
        else -> null
    }
}

@Composable
fun StudentScreen(onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Logout Button
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = { onLogout() }) {
                Text("Logout")
            }
        }

        // Tab Row
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Search Rooms") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("My Bookings") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("View Room Status") }) // New Tab
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Content
        when (selectedTab) {
            0 -> SearchRoomsTab()
            1 -> MyBookingsTab()
            2 -> ViewRoomStatusTab() // New Tab
        }
    }
}

@Composable
fun ViewRoomStatusTab() {
    var selectedRoom by remember { mutableStateOf("") }
    var selectedDay by remember { mutableStateOf("Monday") }
    var selectedTimeslot by remember { mutableStateOf("09:00 AM - 10:00 AM") }
    var computersStatus by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }

    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val timeSlots = listOf("09:00 AM - 10:00 AM", "10:00 AM - 11:00 AM", "11:00 AM - 12:00 PM")
    val rooms = remember { mutableStateListOf<String>() }

    // Load available rooms
    LaunchedEffect(Unit) {
        rooms.clear()
        rooms.addAll(DatabaseHelper.fetchRooms())
    }

    // Fetch computers' status dynamically
    LaunchedEffect(selectedRoom, selectedDay, selectedTimeslot) {
        if (selectedRoom.isNotEmpty()) {
            computersStatus = DatabaseHelper.fetchRoomComputerStatus(selectedRoom, selectedDay, selectedTimeslot)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("View Room Status", style = MaterialTheme.typography.h6)

        Spacer(modifier = Modifier.height(8.dp))

        // Room Selection Dropdown
        Text("Select Room:")
        DropdownMenuOptions(
            selectedOption = selectedRoom,
            options = rooms,
            onOptionSelected = { selectedRoom = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Day Selection Dropdown
        Text("Select Day:")
        DropdownMenuOptions(
            selectedOption = selectedDay,
            options = daysOfWeek,
            onOptionSelected = { selectedDay = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Timeslot Selection Dropdown
        Text("Select Timeslot:")
        DropdownMenuOptions(
            selectedOption = selectedTimeslot,
            options = timeSlots,
            onOptionSelected = { selectedTimeslot = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Graphical Representation
        if (computersStatus.isNotEmpty()) {
            Text("Room Layout:", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable Room Layout Grid
            val scrollState = rememberScrollState()

            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) { // Restrict height to make it scrollable
                Row {
                    // Scrollable Column
                    Box(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            computersStatus.chunked(5).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    row.forEach { computer ->
                                        val color = when (computer["status"]) {
                                            "free" -> MaterialTheme.colors.primary // Free: Green
                                            "reserved" -> MaterialTheme.colors.error // Reserved: Red
                                            "user" -> MaterialTheme.colors.secondary // User's booking: Purple
                                            else -> MaterialTheme.colors.background // Default: Grey
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp) // Increase size if needed for better spacing
                                                .background(color)
                                                .border(1.dp, MaterialTheme.colors.onSurface),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center // Center items vertically
                                            ) {
                                                Text(
                                                    text = computer["id"] ?: "",
                                                    style = MaterialTheme.typography.body1, // Adjust style for readability
                                                    maxLines = 1 // Prevent overflow
                                                )
                                                Spacer(modifier = Modifier.height(4.dp)) // Add space between ID and OS type
                                                Text(
                                                    text = computer["os_type"] ?: "",
                                                    style = MaterialTheme.typography.caption, // Smaller font for the OS type
                                                    maxLines = 1 // Prevent overflow
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    // Visible Scrollbar
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }
        } else if (selectedRoom.isNotEmpty()) {
            Text("No computers available for the selected time.")
        }

    }
}


@Composable
fun SearchRoomsTab() {
    var selectedRoom by remember { mutableStateOf("") }
    var selectedOS by remember { mutableStateOf("Windows") }
    var selectedComputer by remember { mutableStateOf("") }
    var selectedDay by remember { mutableStateOf("Monday") }
    var selectedTimeslot by remember { mutableStateOf("09:00 AM - 10:00 AM") }
    var bookingResult by remember { mutableStateOf("") }

    val osOptions = listOf("Windows", "Mac", "Linux")
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val timeSlots = listOf("09:00 AM - 10:00 AM", "10:00 AM - 11:00 AM", "11:00 AM - 12:00 PM")

    val rooms = remember { mutableStateListOf<String>() }
    val computers = remember { mutableStateListOf<String>() }

    // Load rooms from the database
    LaunchedEffect(Unit) {
        rooms.clear()
        rooms.addAll(DatabaseHelper.fetchRooms())
    }

    // Load computers dynamically based on the selected room and OS
    LaunchedEffect(selectedRoom, selectedOS) {
        if (selectedRoom.isNotEmpty()) {
            computers.clear()
            computers.addAll(DatabaseHelper.fetchAvailableComputers(selectedRoom, selectedOS))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("Book a Computer", style = MaterialTheme.typography.h6)

        // Room Selection Dropdown
        Text("Select Room:")
        DropdownMenuOptions(
            selectedOption = selectedRoom,
            options = rooms,
            onOptionSelected = { selectedRoom = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // OS Selection Dropdown
        Text("Select Operating System:")
        DropdownMenuOptions(
            selectedOption = selectedOS,
            options = osOptions,
            onOptionSelected = { selectedOS = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Computer Selection Dropdown
        Text("Select Computer:")
        DropdownMenuOptions(
            selectedOption = selectedComputer,
            options = computers,
            onOptionSelected = { selectedComputer = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Day Selection Dropdown
        Text("Select Day:")
        DropdownMenuOptions(
            selectedOption = selectedDay,
            options = daysOfWeek,
            onOptionSelected = { selectedDay = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Timeslot Selection Dropdown
        Text("Select Timeslot:")
        DropdownMenuOptions(
            selectedOption = selectedTimeslot,
            options = timeSlots,
            onOptionSelected = { selectedTimeslot = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Book Button
        Button(onClick = {
            bookingResult = DatabaseHelper.bookComputer(
                room = selectedRoom,
                computer = selectedComputer,
                day = selectedDay,
                timeslot = selectedTimeslot,
                username = "student123" // Hardcoded user for now
            )
        }) {
            Text("Book Computer")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Booking Result
        Text(bookingResult, style = MaterialTheme.typography.h6)
    }
}
@Composable
fun MyBookingsTab() {
    var bookings by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var username by remember { mutableStateOf("student123") } // Replace with the actual logged-in username
    var message by remember { mutableStateOf("") }

    // Fetch bookings for the current user
    LaunchedEffect(username) {
        bookings = DatabaseHelper.fetchBookingsForUser(username)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("My Bookings", style = MaterialTheme.typography.h6)

        Spacer(modifier = Modifier.height(16.dp))

        if (bookings.isEmpty()) {
            Text("No bookings found.")
        } else {
            LazyColumn {
                itemsIndexed(bookings) { index, booking ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .background(MaterialTheme.colors.surface)
                            .border(1.dp, MaterialTheme.colors.onSurface)
                    ) {
                        Text("Room: ${booking["room"]}")
                        Text("Computer: ${booking["computer_id"]}")
                        Text("Day: ${booking["day"]}")
                        Text("Timeslot: ${booking["timeslot"]}")
                        Button(
                            onClick = {
                                val bookingId = booking["booking_id"] ?: return@Button
                                if (DatabaseHelper.cancelBooking(bookingId)) {
                                    message = "Booking canceled successfully."
                                    bookings = bookings.filter { it["booking_id"] != bookingId }
                                } else {
                                    message = "Failed to cancel booking."
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Cancel Booking")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (message.isNotEmpty()) {
            Text(message, color = MaterialTheme.colors.primary)
        }
    }
}

fun searchRooms(building: String, osType: String) {
    // Replace this with actual SQLite query logic
    println("Searching for rooms in $building with OS: $osType")
}
@Composable
fun AdminScreen(onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Logout Button
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = { onLogout() }) {
                Text("Logout")
            }
        }

        // Tab Row
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Add Room") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("View Bookings") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Edit Computer") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("View Room Status") }) // New Tab
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Content
        when (selectedTab) {
            0 -> AddRoomTab()
            1 -> ViewBookingsTab()
            2 -> EditComputerTab()
            3 -> ViewRoomStatusTab() // New Tab
        }
    }
}
@Composable
fun EditComputerTab() {
    var selectedRoom by remember { mutableStateOf("") }
    var selectedComputer by remember { mutableStateOf("") }
    var selectedOS by remember { mutableStateOf("Windows") }
    var message by remember { mutableStateOf("") }

    val osOptions = listOf("Windows", "Mac", "Linux")
    val rooms = remember { mutableStateListOf<String>() }
    val computers = remember { mutableStateListOf<String>() }

    // Load available rooms
    LaunchedEffect(Unit) {
        rooms.clear()
        rooms.addAll(DatabaseHelper.fetchRooms())
    }

    // Load computers for the selected room
    LaunchedEffect(selectedRoom) {
        if (selectedRoom.isNotEmpty()) {
            computers.clear()
            computers.addAll(DatabaseHelper.fetchComputersInRoom(selectedRoom))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("Edit Computer OS Type", style = MaterialTheme.typography.h6)

        Spacer(modifier = Modifier.height(8.dp))

        // Room Selection Dropdown
        Text("Select Room:")
        DropdownMenuOptions(
            selectedOption = selectedRoom,
            options = rooms,
            onOptionSelected = { selectedRoom = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Computer Selection Dropdown
        if (selectedRoom.isNotEmpty()) {
            Text("Select Computer:")
            DropdownMenuOptions(
                selectedOption = selectedComputer,
                options = computers,
                onOptionSelected = { selectedComputer = it }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // OS Type Selection Dropdown
        if (selectedComputer.isNotEmpty()) {
            Text("Select New OS Type:")
            DropdownMenuOptions(
                selectedOption = selectedOS,
                options = osOptions,
                onOptionSelected = { selectedOS = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Update Computer Button
        Button(onClick = {
            if (selectedRoom.isNotEmpty() && selectedComputer.isNotEmpty()) {
                val success = DatabaseHelper.updateComputerOSType(selectedRoom, selectedComputer, selectedOS)
                message = if (success) "Computer OS updated successfully!" else "Failed to update computer OS."
            } else {
                message = "Please select a room and computer."
            }
        }) {
            Text("Update Computer OS")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Message
        if (message.isNotEmpty()) {
            Text(message, color = MaterialTheme.colors.primary)
        }
    }
}


@Composable
fun ViewBookingsTab() {
    var selectedRoom by remember { mutableStateOf("") }
    var selectedDay by remember { mutableStateOf("Monday") }
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    var bookings by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }

    val rooms = remember { mutableStateListOf<String>() }

    // Load rooms dynamically
    LaunchedEffect(Unit) {
        rooms.clear()
        rooms.addAll(DatabaseHelper.fetchRooms())
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("View Bookings", style = MaterialTheme.typography.h6)

        Spacer(modifier = Modifier.height(8.dp))

        // Room Selection Dropdown
        Text("Select Room:")
        DropdownMenuOptions(
            selectedOption = selectedRoom,
            options = rooms,
            onOptionSelected = { selectedRoom = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Day Selection Dropdown
        Text("Select Day:")
        DropdownMenuOptions(
            selectedOption = selectedDay,
            options = daysOfWeek,
            onOptionSelected = { selectedDay = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Fetch Bookings Button
        Button(onClick = {
            bookings = DatabaseHelper.fetchBookingsForRoomAndDay(selectedRoom, selectedDay)
        }) {
            Text("Fetch Bookings")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display Bookings
        if (bookings.isNotEmpty()) {
            LazyColumn {
                itemsIndexed(bookings) { index, booking ->
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text("Booking ID: ${booking["booking_id"]}", textAlign = TextAlign.Start)
                        Text("Computer ID: ${booking["computer_id"]}")
                        Text("Timeslot: ${booking["timeslot"]}")
                        Text("Booked by: ${booking["username"]}")
                        Divider()
                    }
                }
            }
        } else if (selectedRoom.isNotEmpty()) {
            Text("No bookings found for $selectedRoom on $selectedDay.")
        }
    }
}


@Composable
fun AddRoomTab() {
    var roomNumber by remember { mutableStateOf("") }
    var computerCount by remember { mutableStateOf("") }
    var computerOSList by remember { mutableStateOf<List<String>>(emptyList()) }
    val osOptions = listOf("Windows", "Mac", "Linux")

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("Add Room", style = MaterialTheme.typography.h6)

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = roomNumber,
            onValueChange = { roomNumber = it },
            label = { Text("Room Number") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = computerCount,
            onValueChange = {
                computerCount = it
                val count = it.toIntOrNull() ?: 0
                if (count % 5 == 0 && count > 0) {
                    computerOSList = List(count) { "Windows" } // Default OS for each computer
                } else {
                    computerOSList = emptyList()
                }
            },
            label = { Text("Number of Computers (multiple of 5)") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (computerOSList.isNotEmpty()) {
            Text("Set OS for each computer:")
            Spacer(modifier = Modifier.height(8.dp))

            val scrollState = rememberScrollState()

            Box(modifier = Modifier.fillMaxHeight(0.5f)) { // Restrict height to half the screen
                Row {
                    // Scrollable LazyColumn
                    Box(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            computerOSList.forEachIndexed { index, os ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Computer ${index + 1}:",
                                        modifier = Modifier.weight(1f).padding(8.dp)
                                    )
                                    DropdownMenuOptions(
                                        selectedOption = os,
                                        options = osOptions,
                                        onOptionSelected = { newOS ->
                                            computerOSList = computerOSList.toMutableList().apply {
                                                this[index] = newOS
                                            }
                                        }
                                    )
                                }
                                Divider() // Optional: Adds a separator
                            }
                        }
                    }

                    // Scrollbar
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val count = computerCount.toIntOrNull() ?: 0
            if (count % 5 == 0 && count > 0) {
                saveRoomToDatabase(roomNumber, computerOSList)
            } else {
                println("Number of computers must be a multiple of 5.")
            }
        }) {
            Text("Add Room")
        }
    }
}

@Composable
fun DropdownMenuOptions(
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(8.dp)) {
        Text(
            selectedOption,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(8.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(onClick = {
                    onOptionSelected(option)
                    expanded = false
                }) {
                    Text(option)
                }
            }
        }
    }
}


fun bookComputer(building: String, day: String, timeslot: String, studentUsername: String): String {
    val sqlFindRoom = """
        SELECT r.id, c.id AS computer_id
        FROM rooms r
        JOIN computers c ON r.id = c.room_id
        WHERE r.building = ? AND NOT EXISTS (
            SELECT 1 FROM bookings b
            WHERE b.room_id = r.id AND b.computer_id = c.id AND b.day = ? AND b.timeslot = ?
        )
        LIMIT 1;
    """.trimIndent()

    val sqlInsertBooking = """
        INSERT INTO bookings (room_id, computer_id, day, timeslot, student_username) 
        VALUES (?, ?, ?, ?, ?)
    """.trimIndent()

    try {
        DatabaseHelper.getConnection()?.use { connection ->
            // Step 1: Find a free computer
            connection.prepareStatement(sqlFindRoom).use { findStmt ->
                findStmt.setString(1, building)
                findStmt.setString(2, day)
                findStmt.setString(3, timeslot)

                val resultSet = findStmt.executeQuery()
                if (resultSet.next()) {
                    val roomId = resultSet.getInt("id")
                    val computerId = resultSet.getInt("computer_id")
                    val globalId = "$building-$computerId"

                    // Step 2: Insert booking
                    connection.prepareStatement(sqlInsertBooking).use { insertStmt ->
                        insertStmt.setInt(1, roomId)
                        insertStmt.setInt(2, computerId)
                        insertStmt.setString(3, day)
                        insertStmt.setString(4, timeslot)
                        insertStmt.setString(5, studentUsername)
                        insertStmt.executeUpdate()
                    }
                    return "Booking Successful! Computer ID: $globalId"
                } else {
                    return "No available computers in $building for $day at $timeslot."
                }
            }
        }
    } catch (e: SQLException) {
        e.printStackTrace()
        return "An error occurred: ${e.message}"
    }
    return "Booking failed."
}

fun saveRoomToDatabase(roomNumber: String, computerOSList: List<String>) {
    val sqlRoom = "INSERT INTO rooms (building, os_type, computer_count) VALUES (?, ?, ?)"
    val sqlComputer = "INSERT INTO computers (room_id, computer_id, os_type) VALUES (?, ?, ?)"
    var roomId: Int = -1 // Initialize with a default invalid value

    try {
        DatabaseHelper.getConnection()?.use { connection ->
            connection.autoCommit = false // Start transaction

            // Step 1: Insert room
            connection.prepareStatement(sqlRoom).use { stmt ->
                stmt.setString(1, roomNumber)
                stmt.setString(2, computerOSList.firstOrNull() ?: "Unknown") // Use the first OS or default
                stmt.setInt(3, computerOSList.size) // Save computer count
                stmt.executeUpdate()
            }

            // Step 2: Retrieve last inserted room_id
            connection.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT last_insert_rowid() AS id")
                if (rs.next()) {
                    roomId = rs.getInt("id")
                } else {
                    throw IllegalStateException("Failed to retrieve generated room ID.")
                }
            }

            if (roomId == -1) throw IllegalStateException("Room ID is invalid!")

            // Step 3: Insert computers
            connection.prepareStatement(sqlComputer).use { stmt ->
                computerOSList.forEachIndexed { index, os ->
                    stmt.setInt(1, roomId)
                    stmt.setInt(2, index + 1) // Computer ID starts from 1
                    stmt.setString(3, os)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }

            // Commit transaction
            connection.commit()
            println("Room '$roomNumber' with ${computerOSList.size} computers added successfully!")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        println("Error occurred, rolling back transaction...")
    }
}


fun addRoom(roomNumber: String, computerCount: Int) {
    if (computerCount % 5 != 0) {
        println("Number of computers must be a multiple of 5")
        return
    }

    // Replace this with actual SQLite logic to add a room
    println("Added room $roomNumber with $computerCount computers")
}
