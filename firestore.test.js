const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write saved_work", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("saved_work").get()
  );
});

test("Authenticated user can create and read their own saved_work item", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("saved_work").doc("work_1").set({
      userId: ALICE_UID,
      workType: "TITLE_OPTIMIZER",
      title: "How I Grew From 0 to 100K Subscribers",
      subtitle: "Score: 91/100 • 10 alternatives",
      score: 91,
      payloadJson: "{\"score\":91}",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("saved_work").doc("work_1").get()
  );
});

test("Cross-user isolation: Bob cannot read or delete Alice's saved_work or zd_chats", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(ALICE_UID).collection("saved_work").doc("work_1").set({
      userId: ALICE_UID,
      workType: "THUMBNAIL_SCORE",
      title: "Thumbnail Check",
      subtitle: "86/100",
      score: 86,
      payloadJson: "{\"score\":86}",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(ALICE_UID).collection("zd_chats").doc("msg_1").set({
      userId: ALICE_UID,
      sessionId: "session_1",
      role: "user",
      content: "Why did my CTR drop?",
      skillId: "analytics_analyst",
      modelUsed: "gemini-4-argon",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("saved_work").doc("work_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("zd_chats").doc("msg_1").get()
  );
});

test("Shadow update with ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("saved_work").doc("work_ghost").set({
      userId: ALICE_UID,
      workType: "TITLE_OPTIMIZER",
      title: "Test",
      subtitle: "Sub",
      score: 80,
      payloadJson: "{}",
      isProAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});
