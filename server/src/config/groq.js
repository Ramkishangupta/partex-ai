import OpenAI from "openai";
import dotenv from "dotenv";

dotenv.config({ path: '../.env' });

let groqClient = null;

export const getGroqClient = () => {
  if (!groqClient) {
    const apiKey = process.env.GROQ_API_KEY;
    if (!apiKey) {
      console.warn('⚠️  GROQ_API_KEY not configured. LLM extraction will not work.');
      return null;
    }

    groqClient = new OpenAI({
      apiKey: apiKey,
      baseURL: "https://api.groq.com/openai/v1",
    });
    console.log(`✅ Groq client initialized`);
  }
  return groqClient;
};
