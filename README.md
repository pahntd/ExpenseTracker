# Expense Tracker

An Android expense tracking application built with Kotlin and modern Android development practices.

Expense Tracker allows users to manage income and expenses, organize transactions by category, view financial summaries, and manage application settings.

## Screenshots

<p align="center">
  <img src="screenshots/home.png" width="200"/>
  <img src="screenshots/add_expense.png" width="200"/>
  <img src="screenshots/category.png" width="200"/>
  <img src="screenshots/statistics.png" width="200"/>
</p>

<p align="center">
  <img src="screenshots/detail.png" width="200"/>
  <img src="screenshots/settings.png" width="200"/>
  <img src="screenshots/dark_mode.png" width="200"/>
</p>

## Features

- Add, edit, and delete transactions
- Support both income and expense transactions
- Create, edit, and delete categories
- Prevent deletion of categories that are currently in use
- Search transactions
- View total income, total expense, and balance
- View income grouped by category
- View expenses grouped by category
- Dark mode
- Delete all application data
- Automatically initialize default categories
- Material Design UI

## Tech Stack

- Kotlin
- Android SDK
- XML
- MVVM
- Room
- Hilt
- Kotlin Coroutines
- Flow / StateFlow
- Navigation Component
- RecyclerView
- DiffUtil
- View Binding
- Material Components
- SharedPreferences

## Architecture

The application follows the MVVM architecture with a Repository layer.

```text
┌───────────────────────────┐
│         UI Layer          │
│   Fragment / Dialog       │
└─────────────┬─────────────┘
              │
              ▼
┌───────────────────────────┐
│        ViewModel          │
│   UiState / EventState    │
└─────────────┬─────────────┘
              │
              ▼
┌───────────────────────────┐
│        Repository         │
└─────────────┬─────────────┘
              │
              ▼
┌───────────────────────────┐
│           DAO             │
└─────────────┬─────────────┘
              │
              ▼
┌───────────────────────────┐
│       Room Database       │
│   Expense / Category      │
└───────────────────────────┘
