
import axios from 'axios';

const BASE_URL = 'http://localhost:8080';
const USERNAME = 'audit@cityhospital.com'; // Default, adjust if needed
const PASSWORD = 'password';           // Default, adjust if needed

async function checkDuplicates() {
    try {
        console.log('Authenticating...');
        const loginRes = await axios.post(`${BASE_URL}/login`, {
            email: USERNAME,
            password: PASSWORD
        });

        const token = loginRes.data.token;
        console.log('Authentication successful.');

        let allPatients = [];
        let page = 0;
        const size = 50;
        let hasMore = true;

        console.log('Fetching patients...');

        while (hasMore) {
            const res = await axios.get(`${BASE_URL}/hospital/patients`, {
                params: { page, size },
                headers: { Authorization: `Bearer ${token}` }
            });

            const data = res.data;
            // Assuming Page structure: { content: [...], last: boolean, ... } 
            // or similar. Let's handle generic Spring Page format.
            const patients = data.content || data; // 'content' key for Page, or direct list

            if (Array.isArray(patients)) {
                allPatients = [...allPatients, ...patients];
                if (data.last === true || patients.length < size) {
                    hasMore = false;
                } else {
                    page++;
                }
            } else {
                console.error("Unexpected response format:", data);
                hasMore = false;
            }
            process.stdout.write(`\rFetched ${allPatients.length} patients...`);
        }

        console.log('\nAnalyzing for duplicates...');

        console.log(`\nAnalyzing ${allPatients.length} patients...`);

        allPatients.forEach(p => console.log(` - ${p.name} (Phone: ${p.phone}, Email: ${p.email})`));

        const nameMap = new Map();
        const phoneMap = new Map();
        const duplicates = [];

        for (const p of allPatients) {
            const normalizedName = p.name ? p.name.trim().toLowerCase() : '';
            const phone = p.phone ? p.phone.trim() : '';

            // Check Name
            if (normalizedName) {
                if (nameMap.has(normalizedName)) {
                    duplicates.push({ type: 'NAME', value: p.name, patient: p });
                } else {
                    nameMap.set(normalizedName, p);
                }
            }

            // Check Phone
            if (phone) {
                if (phoneMap.has(phone)) {
                    duplicates.push({ type: 'PHONE', value: p.phone, patient: p });
                } else {
                    phoneMap.set(phone, p);
                }
            }
        }

        if (duplicates.length === 0) {
            console.log('\nNo duplicates found.');
        } else {
            console.log(`\nFound ${duplicates.length} potential duplicates:`);
            duplicates.forEach(d => {
                console.log(`TYPE: ${d.type}, Value: "${d.value}", Patient ID: ${d.patient.id}`);
            });
        }

    } catch (error) {
        if (error.response) {
            console.error('API Error:', error.response.status, error.response.data);
        } else {
            console.error('Error:', error.message);
        }
    }
}

checkDuplicates();
