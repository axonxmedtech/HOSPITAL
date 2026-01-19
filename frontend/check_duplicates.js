import axios from 'axios';

const API_URL = 'http://localhost:8080';

async function checkDuplicates() {
    try {
        // 1. Login - trying /login at root
        console.log('Logging in...');
        const loginRes = await axios.post(`${API_URL}/login`, {
            email: 'admin',
            password: 'admin123'
        });

        const token = loginRes.data.accessToken || loginRes.data.token;
        if (!token) {
            console.error('Login failed: No token received');
            return;
        }
        console.log('Login successful.');

        // 2. Fetch Patients (fetch all pages if possible, or large size)
        console.log('Fetching patients...');
        const patientsRes = await axios.get(`${API_URL}/patients?size=2000`, {
            headers: { Authorization: `Bearer ${token}` }
        });

        const patients = patientsRes.data.content || patientsRes.data;
        console.log(`Fetched ${patients.length} patients.`);

        // 3. Find Duplicates
        const nameMap = {};
        const duplicates = [];

        patients.forEach(p => {
            const name = p.name.trim().toLowerCase();
            if (nameMap[name]) {
                nameMap[name].count++;
                nameMap[name].ids.push(p.id);
            } else {
                nameMap[name] = { count: 1, ids: [p.id], originalName: p.name };
            }
        });

        Object.keys(nameMap).forEach(key => {
            if (nameMap[key].count > 1) {
                duplicates.push(nameMap[key]);
            }
        });

        // 4. Report
        if (duplicates.length === 0) {
            console.log('No duplicate patients found.');
        } else {
            console.log(`Found ${duplicates.length} duplicate names:`);
            duplicates.forEach(d => {
                console.log(`- "${d.originalName}": ${d.count} records (IDs: ${d.ids.join(', ')})`);
            });
        }

    } catch (error) {
        console.error('Error:', error.response?.data || error.message);
    }
}

checkDuplicates();
