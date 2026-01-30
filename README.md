# Hospital Management System - Phase 1

A multi-tenant Hospital Management System built with Spring Boot and React.

## 🏥 Overview

This is a **Phase-1 MVP** of a Hospital Management System designed as a multi-tenant SaaS platform. The system supports:

- **Super Admin**: Platform owner who manages hospitals
- **Hospital Admin**: Manages daily OPD operations for their hospital
- **Doctor**: Views appointments and patients

## 🛠️ Tech Stack

### Backend
- Java 17
- Spring Boot 3.2.0
- Spring Security with JWT
- Spring Data JPA
- MySQL 8.0
- Lombok
- Maven

### Frontend
- React 18
- React Router DOM
- Axios
- Tailwind CSS 3
- Vite

## 📋 Features

### Super Admin
- ✅ Separate login at `/platform/login`
- ✅ Create hospitals with admin credentials
- ✅ View all hospitals
- ✅ Activate/Deactivate hospitals
- ❌ Cannot see hospital data (patients, doctors, etc.)

### Hospital Admin
- ✅ Login at `/login`
- ✅ Add and view patients
- ✅ Add and view doctors (creates user account for doctor)
- ✅ Create and view appointments
- ✅ Create and view billing records (simple consultation fees)

### Doctor
- ✅ Login at `/login`
- ✅ View appointments
- ✅ View patients
- ❌ Cannot manage hospital data

## 🔐 Multi-Tenant Architecture

- **One backend, one database**
- **Multi-tenancy via `hospital_id`**
- Super Admin: `hospital_id = NULL`
- Hospital users: `hospital_id = <valid ID>`
- Backend automatically filters all data by `hospital_id`
- JWT tokens contain `hospital_id` for automatic filtering

## 🚀 Setup Instructions

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- MySQL 8.0+
- Node.js 18+ and npm

### Database Setup

1. Create MySQL database:
```sql
CREATE DATABASE hospital_management;
```

2. Update database credentials in `backend/src/main/resources/application.properties`:
```properties
spring.datasource.username=root
spring.datasource.password=your_password
```

3. Create initial Super Admin user (run after first backend startup):
```sql
USE hospital_management;

-- Create Super Admin user
-- Password: admin123 (BCrypt encoded)
INSERT INTO users (email, password, name, role, hospital_id, created_at)
VALUES ('admin@hms.com', '$2a$10$qMUbT7gyNjvCsRS//Gf7g.1vFwZAq9RVSn3qpLuPjUzoB8fz0AxWy', 'Super Admin', 'SUPER_ADMIN', NULL, NOW());
```

### Backend Setup

1. Navigate to backend directory:
```bash
cd backend
```

2. Build the project:
```bash
mvn clean install
```

3. Run the application:
```bash
mvn spring-boot:run
```

Backend will start on `http://localhost:8080`

### Frontend Setup

1. Navigate to frontend directory:
```bash
cd frontend
```

2. Install dependencies:
```bash
npm install
```

3. Start development server:
```bash
npm run dev
```

Frontend will start on `http://localhost:5173`

## 🔑 Default Credentials

### Super Admin
- **URL**: http://localhost:5173/platform/login
- **Email**: admin@hms.com
- **Password**: admin123

### Hospital Users
After Super Admin creates a hospital, use the credentials provided during hospital creation.

## 📁 Project Structure

```
Hospital management/
├── backend/
│   ├── src/main/java/com/hms/
│   │   ├── entity/          # Database entities
│   │   ├── repository/      # JPA repositories
│   │   ├── service/         # Business logic
│   │   │   ├── platform/    # Super Admin services
│   │   │   └── hospital/    # Hospital services
│   │   ├── controller/      # REST controllers
│   │   │   ├── platform/    # Super Admin endpoints
│   │   │   └── hospital/    # Hospital endpoints
│   │   ├── security/        # JWT and security config
│   │   ├── config/          # Spring configuration
│   │   └── dto/             # Data transfer objects
│   └── pom.xml
└── frontend/
    ├── src/
    │   ├── pages/
    │   │   ├── platform/    # Super Admin pages
    │   │   └── hospital/    # Hospital pages
    │   ├── components/      # Reusable components
    │   ├── services/        # API services
    │   └── App.jsx          # Main app with routing
    └── package.json
```

## 🔌 API Endpoints

### Super Admin APIs
- `POST /platform/login` - Super Admin login
- `POST /platform/hospitals` - Create hospital
- `GET /platform/hospitals` - List all hospitals
- `GET /platform/hospitals/{id}` - Get hospital details
- `PUT /platform/hospitals/{id}/status` - Activate/deactivate hospital

### Hospital APIs
- `POST /login` - Hospital user login
- `GET /hospital/patients` - List patients
- `POST /hospital/patients` - Add patient
- `GET /hospital/doctors` - List doctors
- `POST /hospital/doctors` - Add doctor
- `GET /hospital/appointments` - List appointments
- `POST /hospital/appointments` - Create appointment
- `GET /hospital/billing` - List billing records
- `POST /hospital/billing` - Create billing record

## ⚠️ Phase-1 Limitations

**NOT INCLUDED in Phase-1:**
- ❌ Pharmacy management
- ❌ Inventory tracking
- ❌ Lab reports
- ❌ Analytics dashboards
- ❌ Payment gateway
- ❌ PDF generation
- ❌ GST calculations
- ❌ Email/SMS notifications
- ❌ Mobile apps
- ❌ Auto hospital signup

## 🧪 Testing

### Manual Testing Flow

1. **Super Admin Flow**:
   - Login at `/platform/login`
   - Create 2 hospitals (Hospital A, Hospital B)
   - Verify both appear in list
   - Deactivate Hospital B
   - Verify status change

2. **Hospital Admin Flow (Hospital A)**:
   - Login at `/login` with Hospital A admin credentials
   - Add 2 patients
   - Add 1 doctor
   - Create 2 appointments
   - Create billing records

3. **Multi-Tenant Isolation Test**:
   - Login as Hospital B admin
   - Verify Hospital A's data is NOT visible
   - Add own data
   - Verify isolation

4. **Doctor Flow**:
   - Login as doctor
   - View appointments
   - View patients
   - Verify cannot access admin functions

## 📝 Code Quality

- ✅ All classes have comprehensive documentation
- ✅ All methods have detailed comments
- ✅ Inline comments for complex logic
- ✅ No dead code or unused variables
- ✅ Clean, readable, and maintainable code

## 🤝 Contributing

This is a Phase-1 MVP. Future phases will add:
- Pharmacy and inventory management
- Lab reports and diagnostics
- Advanced analytics
- Payment integration
- Mobile applications

## 📄 License

Proprietary - All rights reserved

## 👥 Authors

HMS Development Team - Phase 1

---

**Note**: This is a Phase-1 MVP focused on core OPD functionality. The system is designed to be easily extendable for future phases.
