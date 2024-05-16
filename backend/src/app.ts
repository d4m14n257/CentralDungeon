import express, { Express, Request, Response } from "express";
import cors from "cors"
import dotenv from 'dotenv';
import { systems } from "./router/systems";
import { tags } from "./router/tags";
import { platforms } from "./router/platforms";
import { tables } from "./router/tables";
import { users } from "./router/users";
import { files } from "./router/files";
import generateBase64 from "./helper/generateBase64";

//TODO: Change every status 418 because it isnt a teatpot
//TODO: Configure CORS before finish the back
//TODO: Check injection sql is possible and then fixed it
//TODO: Limit rows in generals views.
//TODO: Check status in put and deleter so that response correctly 
//TODO: Check all response status which it is 200.

dotenv.config();

const app : Express = express();
const port = process.env.PORT;

app.use(express.json());
app.use(cors({
    origin: process.env.ORIGIN_HOST
}));

app.use('/systems', systems);
app.use('/tags', tags);
app.use('/platforms', platforms);
app.use('/tables', tables);
app.use('/users', users);
app.use('/files', files);

/* Delete before */
app.get('/test', async (req: Request, res : Response) => {
    const data = await generateBase64();

    res.status(200).send(data);
})

app.listen(port, () => {
    console.log(`Example app listening on port ${port}`)
});

/*
    WITH RECURSIVE find_first AS (
    SELECT id, name, system_id
    FROM Systems
    WHERE id = '3123213123123dasdasgsdfhghjhfjgf4556'
    UNION ALL
    SELECT s.id, s.name, s.system_id
    FROM Systems s
    JOIN find_first f ON s.id = f.system_id
    )
    SELECT * FROM find_first WHERE system_id is NULL ;

*/