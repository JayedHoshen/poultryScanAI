# 🐔 PoultryScan AI

### AI-Powered Poultry Disease Screening Android Application

PoultryScan AI is an Android-based mobile application designed to help poultry farmers and users perform preliminary poultry disease screening using **Artificial Intelligence and Image Classification**.

The application allows users to capture or select a poultry image, analyze it directly on the device using a **TensorFlow Lite EfficientNetV2-B0 model**, and receive a predicted disease class with confidence scores.

> ⚠️ **Disclaimer:** PoultryScan AI provides an AI-based screening result only. It is not a substitute for professional veterinary diagnosis or treatment.

---

## 📱 Project Overview

PoultryScan AI combines **Android development, Machine Learning, and local data storage** to provide an offline-friendly poultry disease screening experience.

The application currently supports four image classification classes:

- 🦠 Coccidiosis
- 🦠 Newcastle Disease
- 🦠 Salmonellosis
- 🐔 Healthy

The machine learning model runs directly on the user's Android device, so image analysis does not require an internet connection.

---

## ✨ Features

### 🔐 User Authentication

- User registration
- User login
- Password hashing
- Session management
- User-specific scan history

### 📸 Poultry Image Scanning

- Capture image using device camera
- Select image from gallery
- Image preprocessing
- On-device AI analysis
- Disease prediction
- Confidence score
- Class-wise prediction scores
- Uncertain-result handling

### 🦠 Disease Library

The application provides information about supported poultry conditions, including:

- Disease overview
- Common symptoms
- Transmission/spread
- Prevention
- General management guidance
- When to contact a veterinarian

### 📚 Scan History

Users can:

- View previous scans
- See predicted disease
- View confidence score
- View scan date/time
- Open previously saved scan information
- Delete scan records

### ⚡ Offline AI Detection

The TensorFlow Lite model runs locally on the Android device.

This means:

- No cloud API is required for prediction
- No image upload is required for AI inference
- Detection can work offline
- User images remain locally stored

---

## 🧠 Machine Learning Model

PoultryScan AI uses a custom TensorFlow Lite image classification model based on:

### EfficientNetV2-B0

**Input:**

```text
224 × 224 × 3
