
import axios from 'axios';

const BASE_URL = 'http://localhost:8080';
const USERNAME = 'audit@cityhospital.com';
const PASSWORD = 'password';

// List of names to specifically target for deletion based on analysis
const JUNK_NAMES = [
    'fdhksjhiioabdjBX',
    'fgdcsxz',
    '87654',
    'Jhon deo'
];

async function cleanupPatients() {
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

        console.log('Fetching patients to identify targets...');

        while (hasMore) {
            const res = await axios.get(`${BASE_URL}/hospital/patients`, {
                params: { page, size },
                headers: { Authorization: `Bearer ${token}` }
            });

            const data = res.data;
            const patients = data.content || data;

            if (Array.isArray(patients)) {
                allPatients = [...allPatients, ...patients];
                if (data.last === true || patients.length < size) {
                    hasMore = false;
                } else {
                    page++;
                }
            } else {
                hasMore = false;
            }
        }

        console.log(`Scanned ${allPatients.length} patients.`);

        const targets = allPatients.filter(p => JUNK_NAMES.includes(p.name));

        if (targets.length === 0) {
            console.log('No matching junk records found to delete.');
            return;
        }

        console.log(`Found ${targets.length} records to delete:`);
        targets.forEach(t => {
            console.log(` - ${t.name} (PublicID: ${t.publicId})`);
            console.log(JSON.stringify(t, null, 2));
        });

        console.log('\nStarting deletion...');

        for (const t of targets) {
            try {
                process.stdout.write(`Deleting ${t.name}... `);
                await axios.delete(`${BASE_URL}/hospital/patients/${t.publicId}`, {
                    headers: { Authorization: `Bearer ${token}` },
                    params: { reason: 'Data cleanup - Junk record' }
                });
                console.log('DONE');
            } catch (delErr) {
                console.log('FAILED');
                if (delErr.response) {
                    console.error('  Error:', delErr.response.status, delErr.response.data);
                } else {
                    console.error('  Error:', delErr.message);
                }
            }
        }

        console.log('\nCleanup complete.');

    } catch (error) {
        if (error.response) {
            console.error('API Error:', error.response.status, error.response.data);
        } else {
            console.error('Error:', error.message);
        }
    }
}

cleanupPatients();
