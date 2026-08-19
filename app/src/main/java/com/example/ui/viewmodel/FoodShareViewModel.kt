package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.local.*
import com.example.data.model.*
import kotlinx.coroutines.flow.*

import kotlinx.coroutines.launch

class FoodShareViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FoodShareRepository()
    private val geminiService = GeminiService()

    // Current logged in user
    val currentUser: StateFlow<UserEntity?> = repository.currentUser

    // List of all donations (filtered or entire depending on role)
    val allDonations: StateFlow<List<DonationEntity>> = repository.getAllDonations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Donations specifically available (status = Created) for NGOs to accept
    val availableDonations: StateFlow<List<DonationEntity>> = repository.getAvailableDonations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Food Analysis entries
    val foodAnalyses: StateFlow<List<FoodAnalysisEntity>> = repository.getAllFoodAnalyses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI Predictions
    val predictions: StateFlow<List<PredictionEntity>> = repository.getAllPredictions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notifications list filtered by current user and role
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notifications: StateFlow<List<NotificationEntity>> = currentUser.flatMapLatest { user ->
        val userId = user?.id ?: ""
        val role = user?.role ?: "donor"
        repository.getNotifications(userId, role)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = notifications.map { list ->
        list.count { !it.isRead }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markNotificationAsRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            val user = currentUser.value
            repository.markAllNotificationsAsRead(user?.id ?: "")
        }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch {
            repository.deleteNotification(id)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            val user = currentUser.value
            repository.clearAllNotifications(user?.id ?: "", user?.role ?: "donor")
        }
    }

    // Admin Users monitor
    val usersList: StateFlow<List<UserEntity>> = repository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived Statistics
    val lastUpdated = MutableStateFlow(System.currentTimeMillis())

    val totalNgosHelped = allDonations.map { list ->
        val user = currentUser.value
        if (user == null) 0
        else {
            if (user.role == "donor") {
                list.filter { it.donorId == user.id && it.ngoName.isNotBlank() }
                    .map { it.ngoName }.distinct().size
            } else {
                list.filter { it.ngoId == user.id && it.donorName.isNotBlank() }
                    .map { it.donorName }.distinct().size
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val rescuedMeals = allDonations.map { list ->
        val user = currentUser.value
        if (user == null || user.role != "ngo") 0
        else list.filter { it.ngoId == user.id && it.status == "Completed" }.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val completedPickups = allDonations.map { list ->
        val user = currentUser.value
        if (user == null || user.role != "ngo") 0
        else list.count { it.ngoId == user.id && it.status == "Completed" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val logisticsEfficiency = allDonations.map { list ->
        val user = currentUser.value
        if (user == null || user.role != "ngo") 100
        else {
            val myDonations = list.filter { it.ngoId == user.id && it.status == "Completed" }
            if (myDonations.isEmpty()) 100
            else {
                // Heuristic calculation: higher is better
                val score = 90 + (myDonations.size % 10)
                score.coerceAtMost(100)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

    // UI Operation States
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage

    // Prediction state
    private val _predictionResult = MutableStateFlow<PredictionResult?>(null)
    val predictionResult: StateFlow<PredictionResult?> = _predictionResult

    // Freshness state
    private val _freshnessResult = MutableStateFlow<FreshnessResult?>(null)
    val freshnessResult: StateFlow<FreshnessResult?> = _freshnessResult

    // AI Feature User-Facing Error Message state
    private val _aiErrorMessage = MutableStateFlow<String?>(null)
    val aiErrorMessage: StateFlow<String?> = _aiErrorMessage

    // Verification Authority State
    private val verificationAuthority by lazy { FoodVerificationAuthority(getApplication()) }

    private val _verificationResult = MutableStateFlow<FoodVerificationResult?>(null)
    val verificationResult: StateFlow<FoodVerificationResult?> = _verificationResult

    private val _verificationStepText = MutableStateFlow<String>("Checking image...")
    val verificationStepText: StateFlow<String> = _verificationStepText

    fun resetVerificationState() {
        _verificationResult.value = null
        _verificationStepText.value = "Checking image..."
    }

    fun verifyDonationImage(bitmap: android.graphics.Bitmap, quantity: Int = 10, title: String = "Donation") {
        viewModelScope.launch {
            _isLoading.value = true
            _verificationStepText.value = "Checking image..."
            try {
                kotlinx.coroutines.delay(200)
                _verificationStepText.value = "Detecting food..."
                kotlinx.coroutines.delay(200)
                _verificationStepText.value = "Analyzing food condition..."
                
                val result = verificationAuthority.verifyImage(bitmap, quantity, title)
                _verificationResult.value = result
            } catch (e: Exception) {
                _verificationResult.value = FoodVerificationResult(
                    verificationStatus = VerificationStatus.ANALYSIS_FAILED,
                    isFood = false,
                    reason = "Unable to analyze image. Please try again.",
                    canPublish = false
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    // AI Summary state

    private val _donationSummary = MutableStateFlow<String?>(null)
    val donationSummary: StateFlow<String?> = _donationSummary

    // AI Recommendations state
    private val _aiRecommendations = MutableStateFlow<String?>(null)
    val aiRecommendations: StateFlow<String?> = _aiRecommendations

    // AI Chat Messages state (Message, IsUser)
    private val _chatMessages = MutableStateFlow<List<Pair<String, Boolean>>>(
        listOf("Hello! I am your FoodShare AI. How can I help you reduce food waste today?" to false)
    )
    val chatMessages: StateFlow<List<Pair<String, Boolean>>> = _chatMessages

    init {
        viewModelScope.launch {
            repository.checkSession()
            // Touch verificationAuthority to run control test & prewarm TFLite models
            val _prewarm = verificationAuthority
        }
        
        // Update last updated timestamp whenever donations change
        allDonations.onEach {
            lastUpdated.value = System.currentTimeMillis()
        }.launchIn(viewModelScope)
    }

    fun logout(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            var isSuccess = true
            try {
                repository.logout()
            } catch (e: Exception) {
                Log.e("FoodShareViewModel", "Logout exception: ${e.localizedMessage}")
                isSuccess = false
            } finally {
                _isLoading.value = false
                onComplete(isSuccess)
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            lastUpdated.value = System.currentTimeMillis()
            // The flows automatically refresh due to SnapshotListeners in Repository
        }
    }

    fun isGeminiApiAvailable(): Boolean = geminiService.isKeyValid()

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
    }

    // --- Auth Actions ---
    fun login(email: String, passwordHash: String, role: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.loginWithResult(email, passwordHash, role)
                result.onSuccess {
                    _successMessage.value = "Welcome back!"
                    onSuccess()
                }.onFailure { ex ->
                    _errorMessage.value = ex.localizedMessage ?: "Authentication failed."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Authentication failed."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(email: String, name: String, passwordHash: String, role: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.registerWithResult(email, name, passwordHash, role)
                result.onSuccess {
                    _successMessage.value = "Account created successfully!"
                    onSuccess()
                }.onFailure { ex ->
                    _errorMessage.value = ex.localizedMessage ?: "Registration failed."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Registration failed."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetPassword(email: String, newPasswordHash: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val success = repository.resetPassword(email, newPasswordHash)
                if (success) {
                    _successMessage.value = "Password reset email sent!"
                    onSuccess()
                } else {
                    _errorMessage.value = "Reset failed. Email not found."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Reset failed."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _isUploadingProfileImage = MutableStateFlow(false)
    val isUploadingProfileImage: StateFlow<Boolean> = _isUploadingProfileImage

    fun updateProfile(name: String, email: String, passwordHash: String, imageUri: android.net.Uri? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val success = repository.updateProfile(name, email, passwordHash, imageUri)
                if (success) {
                    _successMessage.value = "Profile updated successfully!"
                } else {
                    _errorMessage.value = "Failed to update profile."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update profile."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadProfileImage(imageUri: android.net.Uri, context: android.content.Context? = null, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isUploadingProfileImage.value = true
            _errorMessage.value = null
            try {
                val url = repository.uploadProfileImage(imageUri, context)
                if (url.isNotBlank()) {
                    _successMessage.value = "Profile picture updated successfully."
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                onResult(false)
            } finally {
                _isUploadingProfileImage.value = false
            }
        }
    }

    fun removeProfileImage(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isUploadingProfileImage.value = true
            try {
                val success = repository.removeProfileImage()
                if (success) {
                    _successMessage.value = "Profile picture removed."
                    onResult(true)
                } else {
                    _errorMessage.value = "Failed to remove photo."
                    onResult(false)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to remove photo."
                onResult(false)
            } finally {
                _isUploadingProfileImage.value = false
            }
        }
    }

    fun updateFullProfile(
        name: String,
        contactPerson: String,
        phone: String,
        email: String,
        address: String,
        city: String,
        state: String,
        pincode: String,
        description: String,
        registrationId: String = "",
        missionStatement: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val success = repository.updateFullProfile(
                    name = name,
                    contactPerson = contactPerson,
                    phone = phone,
                    email = email,
                    address = address,
                    city = city,
                    state = state,
                    pincode = pincode,
                    description = description,
                    registrationId = registrationId,
                    missionStatement = missionStatement
                )
                if (success) {
                    _successMessage.value = "Profile updated successfully."
                    onComplete(true)
                } else {
                    _errorMessage.value = "Failed to update profile."
                    onComplete(false)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update profile."
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateNgoProfile(
        name: String = "",
        phone: String = "",
        missionStatement: String? = null,
        licenseNumber: String? = null,
        registrationId: String? = null,
        address: String? = null,
        operatingCities: String? = null,
        vehicleFleetCount: Int? = null,
        volunteerCount: Int? = null,
        foodCategories: String? = null,
        operatingHours: String? = null,
        emergencyContact: String? = null,
        serviceArea: String? = null,
        imageUri: android.net.Uri? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val success = repository.updateNgoProfile(
                    name = name,
                    phone = phone,
                    missionStatement = missionStatement,
                    licenseNumber = licenseNumber,
                    registrationId = registrationId,
                    address = address,
                    operatingCities = operatingCities,
                    vehicleFleetCount = vehicleFleetCount,
                    volunteerCount = volunteerCount,
                    foodCategories = foodCategories,
                    operatingHours = operatingHours,
                    emergencyContact = emergencyContact,
                    serviceArea = serviceArea,
                    imageUri = imageUri
                )
                if (success) {
                    _successMessage.value = "NGO Profile updated successfully!"
                } else {
                    _errorMessage.value = "Failed to update NGO profile."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update NGO profile."
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- Donation Actions ---
    fun createDonation(
        title: String,
        foodType: String,
        eventType: String,
        quantity: Int,
        expectedGuests: Int,
        pickupTime: String,
        expiryTime: String,
        location: String,
        description: String,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        imageUri: android.net.Uri? = null,
        onSuccess: () -> Unit,
        onError: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val success = repository.createDonation(
                    title, foodType, eventType, quantity, expectedGuests, pickupTime, expiryTime, location, description, latitude, longitude, imageUri
                )
                if (success) {
                    _successMessage.value = "Donation listed successfully!"
                    onSuccess()
                } else {
                    val msg = "Failed to create donation. Check your network or session."
                    _errorMessage.value = msg
                    onError(msg)
                }
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Network or storage error occurred."
                android.util.Log.e("FoodShareViewModel", "createDonation exception: $msg", e)
                _errorMessage.value = msg
                onError(msg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun acceptDonation(donationId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val user = currentUser.value ?: return@launch
                repository.updateDonationStatus(donationId, "Accepted", ngoId = user.id, ngoName = user.name)
                _successMessage.value = "Donation accepted!"
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to accept donation."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateDonationStage(
        donationId: String,
        newStatus: String,
        volunteerName: String? = null,
        volunteerPhone: String? = null,
        volunteerVehicle: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val user = currentUser.value
                val ngoId = if (user?.role == "ngo") user.id else null
                val ngoName = if (user?.role == "ngo") user.name else null

                repository.updateDonationStage(
                    donationId = donationId,
                    newStatus = newStatus,
                    ngoId = ngoId,
                    ngoName = ngoName,
                    volunteerName = volunteerName,
                    volunteerPhone = volunteerPhone,
                    volunteerVehicle = volunteerVehicle
                )
                _successMessage.value = "Status updated: $newStatus"
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update status."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateDonationWorkflow(donationId: String, newStatus: String) {
        updateDonationStage(donationId, newStatus)
    }

    fun updateDonationStatus(donationId: String, newStatus: String) {
        updateDonationStage(donationId, newStatus)
    }

    // --- AI Prediction & Detection Action ---
    fun predictSurplus(eventType: String, expectedGuests: Int) {
        if (!isGeminiApiAvailable()) {
            _aiErrorMessage.value = GeminiService.SERVICE_UNAVAILABLE_MSG
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = geminiService.getSurplusPrediction(eventType, expectedGuests)
                _predictionResult.value = result
                _aiErrorMessage.value = null
                repository.addPrediction(
                    eventType = eventType,
                    expectedGuests = expectedGuests,
                    predictedMeals = result.predictedSurplusMeals,
                    surplusPercentage = result.surplusPercentage,
                    recommendation = result.recommendation
                )
            } catch (e: Exception) {
                _aiErrorMessage.value = GeminiService.SERVICE_UNAVAILABLE_MSG
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun analyzeFoodFreshness(foodType: String) {
        if (!isGeminiApiAvailable()) {
            _aiErrorMessage.value = GeminiService.SERVICE_UNAVAILABLE_MSG
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = geminiService.getFreshnessAnalysis(foodType)
                _freshnessResult.value = result
                _aiErrorMessage.value = null
                repository.addFoodAnalysis(
                    title = foodType,
                    status = result.status,
                    confidence = result.confidence,
                    freshnessPercentage = result.freshnessPercentage
                )
            } catch (e: Exception) {
                _aiErrorMessage.value = GeminiService.SERVICE_UNAVAILABLE_MSG
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateDonationSummary(
        eventName: String,
        foodCategory: String,
        surplusMeals: Int,
        freshness: String,
        location: String
    ) {
        if (!isGeminiApiAvailable()) {
            _donationSummary.value = GeminiService.SERVICE_UNAVAILABLE_MSG
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val summary = geminiService.getDonationSummary(eventName, foodCategory, surplusMeals, freshness, location)
                _donationSummary.value = summary
            } catch (e: Exception) {
                _donationSummary.value = GeminiService.SERVICE_UNAVAILABLE_MSG
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateAiRecommendations(
        eventType: String,
        foodCategory: String,
        expectedGuests: Int
    ) {
        if (!isGeminiApiAvailable()) {
            _aiRecommendations.value = GeminiService.SERVICE_UNAVAILABLE_MSG
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val recommendations = geminiService.getAiRecommendations(eventType, foodCategory, expectedGuests)
                _aiRecommendations.value = recommendations
            } catch (e: Exception) {
                _aiRecommendations.value = GeminiService.SERVICE_UNAVAILABLE_MSG
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- AI Chatbot ---
    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val currentHistory = _chatMessages.value
        _chatMessages.value = currentHistory + (text to true)

        if (!isGeminiApiAvailable()) {
            _chatMessages.value = _chatMessages.value + (GeminiService.SERVICE_UNAVAILABLE_MSG to false)
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val reply = geminiService.getChatbotResponse(currentHistory, text)
                _chatMessages.value = _chatMessages.value + (reply to false)
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + (GeminiService.SERVICE_UNAVAILABLE_MSG to false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteUser(user: UserEntity) {
        viewModelScope.launch {
            repository.deleteUser(user)
        }
    }

    fun deleteDonation(donationId: String) {
        viewModelScope.launch {
            repository.deleteDonation(donationId)
        }
    }

    fun updateDonation(donationId: String, title: String, quantity: Int) {
        viewModelScope.launch {
            repository.updateDonation(donationId, title, quantity)
        }
    }
}
