const functions = require('firebase-functions');
const admin = require('firebase-admin');
const fetch = require('node-fetch');

admin.initializeApp();

// In-memory rate limiting map
// Key: uid, Value: { count: number, resetAt: number }
const rateLimits = new Map();
const RATE_LIMIT_MAX = 30;
const RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000; // 1 hour

exports.geminiProxy = functions.https.onCall(async (data, context) => {
  // 1. Verify Authentication
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated to call this function.'
    );
  }

  const uid = context.auth.uid;

  // 2. Rate Limiting Check
  const now = Date.now();
  let userLimit = rateLimits.get(uid);

  if (!userLimit || userLimit.resetAt < now) {
    userLimit = { count: 1, resetAt: now + RATE_LIMIT_WINDOW_MS };
  } else {
    userLimit.count += 1;
  }
  rateLimits.set(uid, userLimit);

  if (userLimit.count > RATE_LIMIT_MAX) {
    throw new functions.https.HttpsError(
      'resource-exhausted',
      'Rate limit exceeded. Please try again later.'
    );
  }

  // 3. Prepare Request to Gemini API
  // You should store GEMINI_API_KEY as an environment variable or Firebase config
  // e.g., firebase functions:config:set gemini.api_key="YOUR_KEY"
  // For local testing or newer syntax (v2), process.env.GEMINI_API_KEY might be used.
  // Here we use functions.config() fallback.
  const apiKey = process.env.GEMINI_API_KEY || functions.config().gemini?.api_key;
  
  if (!apiKey) {
    console.error('GEMINI_API_KEY not configured on server.');
    throw new functions.https.HttpsError(
      'internal',
      'Server configuration error. API key missing.'
    );
  }

  const model = data.model || 'gemini-1.5-flash';
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;

  // Construct the payload from client data
  const payload = {
    contents: data.contents,
  };
  
  if (data.systemInstruction) {
      payload.systemInstruction = data.systemInstruction;
  }
  
  if (data.generationConfig) {
      payload.generationConfig = data.generationConfig;
  }

  try {
    // 4. Forward to Gemini API
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    });

    const jsonResponse = await response.json();

    if (!response.ok) {
      console.error('Gemini API Error:', jsonResponse);
      throw new functions.https.HttpsError(
        'internal',
        `Gemini API returned an error: ${jsonResponse.error?.message || 'Unknown error'}`
      );
    }

    // 5. Return response
    return jsonResponse;
  } catch (error) {
    console.error('Fetch error:', error);
    throw new functions.https.HttpsError('internal', 'Error communicating with Gemini API.');
  }
});
