import { GoogleGenerativeAI } from '@google/generative-ai';
import dotenv from 'dotenv';

dotenv.config({ path: '../.env' });

let genAI = null;
let model = null;

export const getGeminiModel = () => {
  if (!model) {
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey || apiKey === 'your_gemini_api_key_here') {
      console.warn('⚠️  Gemini API key not configured. LLM extraction will not work.');
      return null;
    }
    genAI = new GoogleGenerativeAI(apiKey);

    const modelName = process.env.GEMINI_MODEL || 'gemini-2.0-flash';

    model = genAI.getGenerativeModel({
      model: modelName,
      generationConfig: {
        temperature: 0.1,           // Low temperature for precise extraction
        topP: 0.95,
        topK: 40,
        maxOutputTokens: 4096,
        responseMimeType: 'application/json',  // Force JSON output
      },
    });
    console.log(`✅ Gemini model initialized (${modelName})`);
  }
  return model;
};

// Get a model instance for chat/RAG (higher temperature for natural responses)
export const getGeminiChatModel = () => {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey || apiKey === 'your_gemini_api_key_here') return null;

  if (!genAI) {
    genAI = new GoogleGenerativeAI(apiKey);
  }

  const modelName = process.env.GEMINI_MODEL || 'gemini-2.0-flash';

  return genAI.getGenerativeModel({
    model: modelName,
    generationConfig: {
      temperature: 0.4,
      topP: 0.95,
      maxOutputTokens: 2048,
    },
  });
};
