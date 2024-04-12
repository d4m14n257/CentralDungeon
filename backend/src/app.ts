import express, { Express } from "express";
import dotenv from 'dotenv';
import { systems } from "./router/systems";
import { tags } from "./router/tags";
import { platforms } from "./router/platforms";
import { tables } from "./router/tables";
import { users } from "./router/users";
import cors from "cors"

//TODO: Change every status 418 because it isnt a teatpot
//TODO: Keep only the number and simbol in utc.
//TODO: Configure CORS before finish the back
//TODO: Check injection sql is possible and then fixed it

dotenv.config();

const app : Express = express();
const port = process.env.PORT;

app.use(express.json());
app.use(cors());

app.use('/systems', systems);
app.use('/tags', tags);
app.use('/platforms', platforms);
app.use('/tables', tables);
app.use('/users', users)

app.listen(port, () => {
    console.log(`Example app listening on port ${port}`)
});