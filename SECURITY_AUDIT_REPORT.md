# Security Audit & Bug Report - Hospital Management System

**Date:** February 14, 2026  
**Auditor:** AI Security Analysis  
**Severity Levels:** 🔴 Critical | 🟠 High | 🟡 Medium | 🟢 Low

---

## 🔴 CRITICAL SECURITY ISSUES

### 1. Authentication Bypass in Development Mode
**Location:** `frontend/src/services/authService.js`  
**Lines:** 95-97, 71-89

```javascript
isAuthenticated: () => {
    // DISABLED FOR DEVELOPMENT - Always return true to bypass authentication
    return true;
    // return !!sessionStorage.getItem('token');
},

getCurrentUser: () => {
    // Return mock user for development
    return {
        id: 1,
        name: "Development User",
        email: "dev@example.com",
        role: "HOSPITAL_ADMIN",
        ...
    };
}
```

**Risk:** Authentication is completely bypassed. Anyone can access any dashboard without credentials.

**Impact:** 
- Unauthorized access to all patient data
- Ability to modify/delete critical medical records
- Complete system compromise

**Fix:**
```javascript
isAuthenticated: () => {
    return !!sessionStorage.getItem('token');
},

getCurrentUser: () => {
    const userStr = sessionStorage.getItem('user');
    if (userStr) {
        return JSON.parse(userStr);
    }
    return null; // Don't return mock user
}
```

---

### 2. No Route Protection
**Location:** `frontend/src/App.jsx`  
**Lines:** 37-82

**Risk:** All dashboard routes are unprotected. Comment says "UNPROTECTED (UI MODE)".

**Impact:**
- Direct URL access to any dashboard without login
- No role-based access control
- Users can access admin/doctor/pharmacy dashboards freely

**Fix:** Implement ProtectedRoute component:
```javascript
const ProtectedRoute = ({ children, allowedRoles }) => {
    const isAuth = authService.isAuthenticated();
    const user = authService.getCurrentUser();
    
    if (!isAuth) {
        return <Navigate to="/login" replace />;
    }
    
    if (allowedRoles && !allowedRoles.includes(user?.role)) {
        return <Navigate to="/unauthorized" replace />;
    }
    
    return children;
};

// Usage:
<Route path="/hospital/admin" element={
    <ProtectedRoute allowedRoles={['HOSPITAL_ADMIN']}>
        <HospitalAdminDashboard />
    </ProtectedRoute>
} />
```

---

### 3. Hardcoded Credentials in Scripts
**Location:** 
- `frontend/cleanupPatients.js` (Line 6-7)
- `frontend/checkDuplicates.js` (Line 6-7)

```javascript
const USERNAME = 'audit@cityhospital.com';
const PASSWORD = 'password';
```

**Risk:** Credentials committed to version control.

**Impact:**
- Credentials exposed in Git history
- Potential unauthorized access if scripts are deployed

**Fix:**
- Use environment variables
- Add these files to .gitignore
- Rotate compromised credentials immediately

```javascript
const USERNAME = process.env.AUDIT_USERNAME;
const PASSWORD = process.env.AUDIT_PASSWORD;
```

---

## 🟠 HIGH SECURITY ISSUES

### 4. CORS Wildcard Configuration
**Location:** 
- `backend/src/main/java/com/hms/controller/hospital/MedicineController.java` (Line 14)
- `backend/src/main/java/com/hms/controller/hospital/HospitalAuditController.java` (Line 19)

```java
@CrossOrigin(origins = "*")
```

**Risk:** Allows requests from ANY origin.

**Impact:**
- CSRF attacks possible
- Malicious websites can make requests to your API
- Data theft through XSS

**Fix:**
```java
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
```

---

### 5. JWT Token Stored in sessionStorage
**Location:** `frontend/src/services/authService.js`

**Risk:** sessionStorage is vulnerable to XSS attacks.

**Impact:**
- Token theft through XSS
- Session hijacking

**Recommendation:**
- Use httpOnly cookies for token storage (requires backend changes)
- Implement token refresh mechanism
- Add CSRF protection

---

### 6. No Input Sanitization on Backend
**Location:** Backend controllers (various)

**Risk:** While no SQL injection found (good use of JPA), there's no explicit input sanitization.

**Impact:**
- Potential XSS through stored data
- Data integrity issues

**Fix:** Add validation annotations:
```java
@NotBlank(message = "Name is required")
@Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
@Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Name must contain only letters")
private String name;
```

---

### 7. Excessive Console Logging
**Location:** Throughout frontend (50+ instances)

**Risk:** Sensitive data logged to browser console.

**Examples:**
- `console.log(user)` - Exposes user data
- `console.error("User Public ID is missing", info.row.original)` - Logs entire user object
- Login errors with full error responses

**Impact:**
- Data leakage in production
- Debugging information exposed to attackers

**Fix:**
- Remove all console.log statements before production
- Use proper logging service
- Implement environment-based logging:

```javascript
const logger = {
    log: (...args) => {
        if (process.env.NODE_ENV === 'development') {
            console.log(...args);
        }
    },
    error: (...args) => {
        if (process.env.NODE_ENV === 'development') {
            console.error(...args);
        }
        // Send to error tracking service in production
    }
};
```

---

## 🟡 MEDIUM SECURITY ISSUES

### 8. No Rate Limiting
**Location:** Backend API endpoints

**Risk:** No rate limiting on login or API endpoints.

**Impact:**
- Brute force attacks on login
- API abuse
- DoS attacks

**Fix:** Implement rate limiting in Spring Boot:
```java
@Bean
public RateLimiter rateLimiter() {
    return RateLimiter.create(10.0); // 10 requests per second
}
```

---

### 9. Weak Password Validation
**Location:** `frontend/src/utils/validation.js` (Line 27-30)

```javascript
password: (value) => {
    if (!value) return null;
    return value.length >= 6 ? null : "Password must be at least 6 characters";
}
```

**Risk:** Only checks length, no complexity requirements.

**Impact:**
- Weak passwords allowed (e.g., "123456")
- Easy to crack

**Fix:**
```javascript
password: (value) => {
    if (!value) return null;
    if (value.length < 8) return "Password must be at least 8 characters";
    if (!/[A-Z]/.test(value)) return "Password must contain uppercase letter";
    if (!/[a-z]/.test(value)) return "Password must contain lowercase letter";
    if (!/[0-9]/.test(value)) return "Password must contain a number";
    if (!/[!@#$%^&*]/.test(value)) return "Password must contain special character";
    return null;
}
```

---

### 10. No HTTPS Enforcement
**Location:** `frontend/src/services/apiService.js`

```javascript
const API_BASE_URL = 'http://localhost:8080';
```

**Risk:** Using HTTP instead of HTTPS.

**Impact:**
- Man-in-the-middle attacks
- Token interception
- Data theft

**Fix:** Use HTTPS in production:
```javascript
const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';
```

---

### 11. No Request Timeout Handling
**Location:** `frontend/src/services/apiService.js` (Line 24)

```javascript
timeout: 10000, // 10 seconds timeout
```

**Risk:** Fixed timeout might be too short for some operations.

**Impact:**
- Failed requests for legitimate slow operations
- Poor user experience

**Fix:** Implement configurable timeouts per endpoint type.

---

## 🟢 LOW PRIORITY ISSUES

### 12. Missing Error Boundaries
**Location:** React components

**Risk:** Unhandled errors crash entire app.

**Fix:** Implement Error Boundary component.

---

### 13. No Content Security Policy (CSP)
**Location:** HTML/Server configuration

**Risk:** No CSP headers to prevent XSS.

**Fix:** Add CSP headers in production.

---

### 14. Duplicate Status Definitions
**Location:** `frontend/src/components/StatusBadge.jsx`

**Risk:** 'CANCELLED', 'UNPAID', 'INACTIVE' defined multiple times.

**Impact:** Inconsistent behavior, maintenance issues.

**Fix:** Remove duplicates, use single source of truth.

---

## 🐛 BUGS & CODE QUALITY ISSUES

### 1. Missing Public ID Handling
**Location:** Multiple dashboards

**Issue:** Code checks for missing publicId but doesn't handle gracefully:
```javascript
console.error("CRITICAL: The following appointments are missing publicId:", ...);
```

**Fix:** Implement fallback ID strategy or prevent creation without publicId.

---

### 2. Inconsistent Error Handling
**Location:** Throughout frontend

**Issue:** Mix of try-catch, .catch(), and no error handling.

**Fix:** Standardize error handling pattern.

---

### 3. Memory Leaks in Modals
**Location:** Various modal components

**Issue:** Event listeners not always cleaned up.

**Fix:** Ensure all useEffect cleanup functions remove listeners.

---

### 4. Race Conditions in Data Loading
**Location:** Dashboard components

**Issue:** Multiple simultaneous API calls without proper state management.

**Fix:** Implement proper loading states and request cancellation.

---

## 📋 COMPLIANCE ISSUES (HIPAA/GDPR)

### 1. No Audit Logging for Data Access
**Issue:** Only modifications logged, not data access.

**HIPAA Requirement:** All PHI access must be logged.

**Fix:** Log all patient data reads.

---

### 2. No Data Encryption at Rest
**Issue:** No mention of database encryption.

**Fix:** Enable database encryption for sensitive fields.

---

### 3. No Session Timeout
**Issue:** Sessions don't expire automatically.

**Fix:** Implement automatic logout after inactivity.

---

### 4. No Data Retention Policy
**Issue:** No automatic data deletion/archival.

**Fix:** Implement data retention policies.

---

## 🎯 IMMEDIATE ACTION ITEMS (Priority Order)

1. **ENABLE AUTHENTICATION** - Remove development bypass
2. **IMPLEMENT ROUTE PROTECTION** - Add ProtectedRoute component
3. **REMOVE HARDCODED CREDENTIALS** - Use environment variables
4. **FIX CORS WILDCARDS** - Restrict to specific origins
5. **REMOVE CONSOLE.LOG** - Clean up production code
6. **STRENGTHEN PASSWORD VALIDATION** - Add complexity requirements
7. **IMPLEMENT RATE LIMITING** - Prevent brute force
8. **ADD HTTPS** - Use secure connections
9. **IMPLEMENT SESSION TIMEOUT** - Auto-logout after inactivity
10. **ADD AUDIT LOGGING** - Log all PHI access

---

## 🔧 RECOMMENDED SECURITY ENHANCEMENTS

1. **Two-Factor Authentication (2FA)**
2. **Password Reset Flow** with email verification
3. **Account Lockout** after failed login attempts
4. **IP Whitelisting** for admin access
5. **Security Headers** (X-Frame-Options, X-Content-Type-Options, etc.)
6. **API Versioning** for backward compatibility
7. **Input Validation Library** (e.g., Joi, Yup)
8. **Automated Security Scanning** in CI/CD
9. **Penetration Testing** before production
10. **Security Training** for development team

---

## 📊 RISK SUMMARY

| Severity | Count | Status |
|----------|-------|--------|
| 🔴 Critical | 3 | **MUST FIX BEFORE PRODUCTION** |
| 🟠 High | 4 | **FIX BEFORE PRODUCTION** |
| 🟡 Medium | 4 | **FIX SOON** |
| 🟢 Low | 3 | **BACKLOG** |

**Overall Risk Level:** 🔴 **CRITICAL - NOT PRODUCTION READY**

---

## ✅ POSITIVE FINDINGS

1. ✅ No SQL injection vulnerabilities (good use of JPA)
2. ✅ No dangerouslySetInnerHTML usage
3. ✅ JWT-based authentication architecture (just disabled)
4. ✅ Role-based access control structure in place
5. ✅ Input validation framework exists
6. ✅ Audit logging infrastructure present
7. ✅ Proper use of HTTPS-ready architecture

---

## 📝 CONCLUSION

The application has a **solid foundation** but is currently in **development mode with security disabled**. The architecture supports proper security, but critical features are commented out or bypassed.

**Before production deployment:**
- Re-enable all authentication and authorization
- Remove development bypasses
- Implement all Critical and High priority fixes
- Conduct thorough security testing
- Perform HIPAA compliance audit

**Estimated effort to production-ready:** 2-3 weeks of focused security work.
