from app import create_app  # SK-QA
from config import SERVER_PORT  # SK-QA
# SK-QA
app = create_app()  # SK-QA
# SK-QA
if __name__ == "__main__":  # SK-QA
    print("All Right Sudhir...........")  # SK-QA
    app.run(host="0.0.0.0", port=SERVER_PORT)  # SK-QA
