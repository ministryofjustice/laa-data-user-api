import os
import psycopg2
from faker import Faker
import uuid
import random
from datetime import datetime

fake = Faker('en_GB')

DB_HOST = os.getenv('DB_HOST', 'localhost')
DB_PORT = os.getenv('DB_PORT', '5432')
DB_USER = os.getenv('DB_USER', 'cptJXFyh0U')
DB_PASSWORD = os.getenv('DB_PASSWORD', 'password')
DB_NAME = os.getenv('DB_NAME', 'data_user_api')

def get_db_connection():
    return psycopg2.connect(
        host=DB_HOST,
        port=DB_PORT,
        user=DB_USER,
        password=DB_PASSWORD,
        dbname=DB_NAME
    )

def generate_data():
    conn = get_db_connection()
    cursor = conn.cursor()

    # Bypass constraint check for firm vs office dependency
    cursor.execute("SET app.internal.pda_sync_bypass_constraint_check = 'true';")

    # Generate Firms
    print("Generating 1000 Firms...")
    firms = []
    firm_types = ['LEGAL_SERVICES_PROVIDER', 'CHAMBERS', 'ADVOCATE']
    for _ in range(1000):
        f_id = str(uuid.uuid4())
        f_code = fake.bothify(text='FIRM-####-####')
        f_name = fake.unique.company()
        f_type = random.choice(firm_types)
        f_enabled = True
        cursor.execute(
            "INSERT INTO firm (id, code, name, type, enabled) VALUES (%s, %s, %s, %s, %s)",
            (f_id, f_code, f_name, f_type, f_enabled)
        )
        firms.append(f_id)

    # Generate Offices for Firms
    print("Generating Offices...")
    offices = []
    for f_id in firms:
        for _ in range(random.randint(1, 4)):
            o_id = str(uuid.uuid4())
            o_code = fake.unique.bothify(text='OFF-####-####')
            o_post_code = fake.postcode()
            o_address1 = fake.street_address()
            o_city = fake.city()
            cursor.execute(
                "INSERT INTO office (firm_id, id, code, post_code, address_line_1, city) VALUES (%s, %s, %s, %s, %s, %s)",
                (f_id, o_id, o_code, o_post_code, o_address1, o_city)
            )
            offices.append((o_id, f_id))

    # Generate Apps
    print("Generating Apps...")
    apps = []
    app_types = ['AUTHZ', 'LAA']
    for _ in range(10):
        a_id = str(uuid.uuid4())
        a_entra_app_id = str(uuid.uuid4())
        a_name = fake.word().capitalize() + " App " + str(uuid.uuid4())[:4]
        a_sec_oid = str(uuid.uuid4())
        a_desc = fake.sentence()
        a_url = fake.url()
        a_type = random.choice(app_types)
        a_entra_oid = str(uuid.uuid4())
        cursor.execute(
            "INSERT INTO app (id, entra_app_id, name, security_group_oid, description, url, app_type, entra_oid) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)",
            (a_id, a_entra_app_id, a_name, a_sec_oid, a_desc, a_url, a_type, a_entra_oid)
        )
        apps.append(a_id)

    # Generate App Roles
    print("Generating App Roles...")
    app_roles = []
    for a_id in apps:
        for _ in range(4):
            ar_id = str(uuid.uuid4())
            ar_desc = fake.job()
            ar_name = fake.word().upper() + "_ROLE_" + str(uuid.uuid4())[:4]
            cursor.execute(
                "INSERT INTO app_role (authz_role, app_id, id, description, name, legacy_sync) VALUES (%s, %s, %s, %s, %s, %s)",
                (False, a_id, ar_id, ar_desc, ar_name, False)
            )
            app_roles.append(ar_id)

    # Generate Entra Users and Profiles
    print("Generating 5000 Entra Users and Profiles...")
    user_statuses = ['ACTIVE', 'DEACTIVE', 'AWAITING_USER_APPROVAL']
    user_types = ['INTERNAL', 'EXTERNAL']
    now = datetime.now()
    
    for count in range(5000):
        u_id = str(uuid.uuid4())
        u_email = fake.unique.email()
        u_entra_oid = str(uuid.uuid4())
        u_fname = fake.first_name()
        u_lname = fake.last_name()
        u_status = random.choice(user_statuses)
        u_type = random.choice(user_types)
        
        is_multi_firm = (u_type == 'EXTERNAL' and random.random() < 0.1) # 10% chance of multi-firm
        
        cursor.execute(
            "INSERT INTO entra_user (created_date, id, created_by, email, entra_oid, first_name, last_name, status, enabled, multi_firm_user) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
            (now, u_id, 'system', u_email, u_entra_oid, u_fname, u_lname, u_status, True, is_multi_firm)
        )

        firm_count = random.randint(2, 4) if is_multi_firm else 1
        selected_firms = random.sample(firms, k=firm_count) if u_type == 'EXTERNAL' else [None]

        is_first_profile = True
        for up_firm_id in selected_firms:
            up_id = str(uuid.uuid4())
            cursor.execute(
                "INSERT INTO user_profile (active_profile, created_date, entra_user_id, firm_id, id, created_by, status, silas_status, user_type) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)",
                (is_first_profile, now, u_id, up_firm_id, up_id, 'system', 'COMPLETE', 'COMPLETE', u_type)
            )
            is_first_profile = False

            if up_firm_id:
                # Assign 1 to 3 offices (Many-to-Many)
                valid_offices = [o[0] for o in offices if o[1] == up_firm_id]
                if valid_offices:
                    selected_offices = random.sample(valid_offices, k=min(len(valid_offices), random.randint(1, 3)))
                    for office_id in selected_offices:
                        cursor.execute(
                            "INSERT INTO user_profile_office (office_id, user_profile_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
                            (office_id, up_id)
                        )
            
            # Assign App Roles (Many-to-Many)
            selected_roles = random.sample(app_roles, k=random.randint(1, 5))
            for r_id in selected_roles:
                cursor.execute(
                    "INSERT INTO user_profile_app_role (app_role_id, user_profile_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
                    (r_id, up_id)
                )

        if count > 0 and count % 500 == 0:
            print(f"Generated {count} users...")

    conn.commit()
    cursor.close()
    conn.close()
    print("Successfully generated 5000 users and massive fake data sets.")

if __name__ == '__main__':
    try:
        generate_data()
    except Exception as e:
        print(f"Error generating data: {e}")
        import traceback
        traceback.print_exc()
        exit(1)
