/**
 * J-AI-mes Gemini API Proxy
 * 
 * Firebase Cloud Function that proxies Gemini API requests from the Android app.
 * This keeps the API key server-side, eliminating user setup friction.
 * 
 * Setup Instructions:
 * 1. Install Firebase CLI: npm install -g firebase-tools
 * 2. Login: firebase login
 * 3. Set Gemini API key: firebase functions:config:set gemini.apikey="YOUR_GEMINI_API_KEY"
 * 4. Deploy: cd firebase && firebase deploy --only functions
 * 
 * Rate limit: 60 requests/hour per authenticated user (anonymous auth).
 */

const { onRequest } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const https = require("https");

admin.initializeApp();

// In-memory rate limiting (resets on cold start, which is acceptable)
const rateLimitMap = new Map();
const RATE_LIMIT = 60;           // requests per window
const RATE_WINDOW_MS = 3600000;  // 1 hour in milliseconds

const GEMINI_BASE_URL = "generativelanguage.googleapis.com";

/**
 * Check if a user has exceeded their rate limit.
 * @param {string} uid - Firebase Auth UID
 * @returns {boolean} true if allowed, false if rate limited
 */
function checkRateLimit(uid) {
  const now = Date.now();
  let userData = rateLimitMap.get(uid);

  if (!userData || now - userData.windowStart > RATE_WINDOW_MS) {
    // New window
    rateLimitMap.set(uid, { windowStart: now, count: 1 });
    return true;
  }

  if (userData.count >= RATE_LIMIT) {
    return false;
  }

  userData.count++;
  return true;
}

/**
 * Verify Firebase Auth token from the Authorization header.
 * @param {string} authHeader - "Bearer <token>"
 * @returns {Promise<object>} decoded token with uid
 */
async function verifyAuthToken(authHeader) {
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    throw new Error("Missing or invalid Authorization header");
  }

  const idToken = authHeader.split("Bearer ")[1];
  return admin.auth().verifyIdToken(idToken);
}

/**
 * Forward a request to the Gemini API.
 * Returns a Promise that resolves with the response body.
 */
function forwardToGemini(apiKey, model, requestBody) {
  return new Promise((resolve, reject) => {
    const path = `/v1beta/models/${model}:generateContent?key=${apiKey}`;
    
    const options = {
      hostname: GEMINI_BASE_URL,
      path: path,
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
    };

    const req = https.request(options, (res) => {
      let data = "";
      res.on("data", (chunk) => { data += chunk; });
      res.on("end", () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve({ statusCode: res.statusCode, body: data });
        } else {
          reject({ statusCode: res.statusCode, body: data });
        }
      });
    });

    req.on("error", (error) => {
      reject({ statusCode: 500, body: JSON.stringify({ error: { message: error.message } }) });
    });

    req.write(JSON.stringify(requestBody));
    req.end();
  });
}

/**
 * Main proxy endpoint.
 * 
 * Expected request body:
 * {
 *   "model": "gemini-3.1-flash-lite-preview",
 *   "contents": [...],                  // Gemini API contents array
 *   "systemInstruction": {...},         // Optional system instruction
 *   "generationConfig": {...},          // Optional generation config
 *   "tools": [...]                      // Optional tools
 * }
 * 
 * Headers:
 *   Authorization: Bearer <firebase-id-token>
 */
exports.geminiProxy = onRequest(
  { 
    region: "europe-west1",
    timeoutSeconds: 120,
    memory: "256MiB",
    cors: true
  },
  async (req, res) => {
    // Only allow POST
    if (req.method !== "POST") {
      res.status(405).json({ error: { message: "Method not allowed" } });
      return;
    }

    try {
      // 1. Verify authentication
      const decodedToken = await verifyAuthToken(req.headers.authorization);
      const uid = decodedToken.uid;

      // 2. Check rate limit (60 requests/hour)
      if (!checkRateLimit(uid)) {
        res.status(429).json({ 
          error: { 
            message: "Rate limit exceeded. Please wait before making more requests.",
            code: 429
          } 
        });
        return;
      }

      // 3. Get API key from Firebase Functions config
      // Set with: firebase functions:config:set gemini.apikey="YOUR_KEY"
      const apiKey = process.env.GEMINI_API_KEY || 
        (require("firebase-functions").config().gemini && 
         require("firebase-functions").config().gemini.apikey);
      
      if (!apiKey) {
        console.error("Gemini API key not configured. Set with: firebase functions:config:set gemini.apikey=\"YOUR_KEY\"");
        res.status(500).json({ 
          error: { message: "Server configuration error" } 
        });
        return;
      }

      // 4. Extract request parameters
      const { model, contents, systemInstruction, generationConfig, tools } = req.body;

      if (!model || !contents) {
        res.status(400).json({ 
          error: { message: "Missing required fields: model, contents" } 
        });
        return;
      }

      // 5. Build Gemini request (forward as-is, the client builds the right format)
      const geminiRequest = { contents };
      if (systemInstruction) geminiRequest.systemInstruction = systemInstruction;
      if (generationConfig) geminiRequest.generationConfig = generationConfig;
      if (tools) geminiRequest.tools = tools;

      // 6. Forward to Gemini
      const response = await forwardToGemini(apiKey, model, geminiRequest);
      
      // 7. Return response
      res.status(200).json(JSON.parse(response.body));

    } catch (error) {
      if (error.statusCode) {
        // Gemini API error - forward it
        try {
          res.status(error.statusCode).json(JSON.parse(error.body));
        } catch (e) {
          res.status(error.statusCode).json({ 
            error: { message: "Upstream API error", code: error.statusCode } 
          });
        }
      } else if (error.message && error.message.includes("Authorization")) {
        res.status(401).json({ error: { message: error.message } });
      } else {
        console.error("Proxy error:", error);
        res.status(500).json({ 
          error: { message: "Internal proxy error" } 
        });
      }
    }
  }
);
