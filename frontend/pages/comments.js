/*
    Master solo puede ver sus comentarios pero no quien.
    Admin es chismoso (Puede ver todo, por ende la informacion si se guarda completa).
    Usuario puede ver sus propios comentarios y si es de master quien fue. De otros usarios no sabes quien fue de ambos lados.
    Mastes al ver los comentarios de un usuario, puede ver los comentarios por otros master (tambien quien), y pero lo de los jugadores no.
    Usuarios que revisan comentarios del master puede ver los comentarios hechos por otros master y otros usuarios. No quien, solo saber que rol fue.
    Saber su rol y que va a que.
*/

import React, { useState } from 'react';
import { TextField, Select, MenuItem, Button, FormControl, InputLabel, Grid } from '@mui/material';

const daysOfWeek = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

export default function ExampleForm () {
  const [schedule, setSchedule] = useState({});

  const handleChange = (day, time) => (event) => {
    setSchedule({ ...schedule, [day]: time });
  };

  const handleSubmit = () => {
    // Aquí puedes enviar el horario a tu backend o realizar cualquier otra acción
    console.log(schedule);
  };

  return (
    <Grid container spacing={2}>
      {daysOfWeek.map((day) => (
        <Grid item xs={12} sm={6} md={4} key={day}>
          <FormControl fullWidth>
            <InputLabel id={`${day}-label`}>{day}</InputLabel>
            <Select
              labelId={`${day}-label`}
              id={day}
              value={schedule[day] || ''}
              onChange={handleChange(day)}
            >
              <MenuItem value="">No Class</MenuItem>
              <MenuItem value="8:00am">8:00am</MenuItem>
              <MenuItem value="9:00am">9:00am</MenuItem>
              {/* Agrega más horarios según tus necesidades */}
            </Select>
          </FormControl>
        </Grid>
      ))}
      <Grid item xs={12}>
        <Button variant="contained" color="primary" onClick={handleSubmit}>Submit</Button>
      </Grid>
    </Grid>
  );
};

// export default ExampleForm;

// import React, { useState } from 'react';
// import { Button, Input, Stack, Typography } from '@mui/material';

// const FileUpload = () => {
//   const [files, setFiles] = useState([]);

//   const handleFileChange = (event) => {
//     const selectedFiles = Array.from(event.target.files);
//     setFiles((prevFiles) => [...prevFiles, ...selectedFiles]);
//   };

//   const handleUpload = () => {
//     // Aquí puedes enviar los archivos al servidor o realizar cualquier otra acción
//     console.log(files);
//     // Limpia la lista de archivos después de subirlos
//     setFiles([]);
//   };

//   return (
//     <div>
//       <Stack spacing={2} direction="row">
//         <Input type="file" onChange={handleFileChange} multiple />
//         <Button variant="contained" onClick={handleUpload}>Upload</Button>
//       </Stack>
//       <Typography variant="body1" component="div">
//         {files.map((file, index) => (
//           <div key={index}>{file.name}</div>
//         ))}
//       </Typography>
//     </div>
//   );
// };

// export default FileUpload;
