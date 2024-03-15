import { useState } from 'react';

import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TableCellUsers from './users/TableCellUsers';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';

import { global } from '@/styles/global';

const EnhancedTableToolbar = (props) => {
    const { title } = props;
    const numSelected = 0;
  
    return (
        <Toolbar
            sx={{
                pl: { sm: 2 },
                pr: { xs: 1, sm: 1 },
                // ...(numSelected > 0 && {
                //     bgcolor: (theme) =>
                //     alpha(theme.palette.primary.main, theme.palette.action.activatedOpacity),
                // }),
            }}
        >
            {numSelected > 0 ? (
                <Typography
                    sx={{ flex: '1 1 100%' }}
                    color="inherit"
                    variant="subtitle1"
                    component="div"
                >
                    {numSelected} selected
                </Typography>
                ) : (
                <Typography
                    sx={{ flex: '1 1 100%' }}
                    variant="h5"
                    id="tableTitle"
                    component="div"
                >
                    {title}
                </Typography>
            )}

            {/* {numSelected > 0 ? (
                <Tooltip title="Delete">
                    <IconButton>
                        <DeleteIcon />
                    </IconButton>
                </Tooltip>
                ) : (
                <Tooltip title="Filter list">
                    <IconButton>
                        <FilterListIcon />
                    </IconButton>
                </Tooltip>
            )} */}
        </Toolbar>
    );
  }

export default function TableComponent (props) {
    const { title, columns, rows, checkbox, minWidth, Actions } = props;

    const [page, setPage] = useState(0);
    const [rowsPerPage, setRowsPerPage] = useState(10);
    const [haveCheckbox, setHaveCheckbox] = useState(checkbox ? true : false);

    const handleChangePage = (event, newPage) => {
        setPage(newPage);
    };

    const handleChangeRowsPerPage = (event) => {
        setRowsPerPage(+event.target.value);
        setPage(0);
    };

    return (
        <Paper sx={{...global.border, width: '100%', overflow: 'hidden'}}>
            <Box sx={{margin: 2}}>
                <EnhancedTableToolbar 
                    title={title}
                />
                <TableContainer sx={{ maxHeight: 500 }}>
                    <Table stickyHeader aria-label="sticky table" >
                        <TableHead>
                            <TableRow>
                                {haveCheckbox &&
                                    <TableCell padding="checkbox">
                                        <Checkbox
                                            color="primary"
                                            // indeterminate={numSelected > 0 && numSelected < rowCount}
                                            // checked={rowCount > 0 && numSelected === rowCount}
                                            // onChange={onSelectAllClick}
                                            // inputProps={{
                                            //     'aria-label': 'select all desserts',
                                            // }}
                                        /> 
                                    </TableCell>
                                }
                                {columns.map((column) => (
                                    <TableCell
                                        key={column.id}
                                        style={{ minWidth: minWidth }}
                                    >
                                        <Typography variant='subtitle1' sx={{fontWeight: '700'}}>
                                            {column.label}
                                        </Typography>
                                        {column.additional &&
                                            <><br/>{column.additional}</>
                                        }
                                    </TableCell>
                                ))}
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {rows.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                            .map((row) => {
                                const isItemSelected = false

                                return (
                                    <TableRow 
                                        hover 
                                        role="checkbox" 
                                        tabIndex={-1} 
                                        key={row.id}
                                        aria-checked={isItemSelected}
                                        selected={isItemSelected}
                                    >   
                                        {haveCheckbox &&
                                            <TableCell padding="checkbox">
                                                <Checkbox
                                                    color="primary"
                                                    checked={isItemSelected}
                                                    // inputProps={{
                                                    //     'aria-labelledby': labelId,
                                                    // }}
                                                />
                                            </TableCell>
                                        }
                                        {columns.map((column) => {
                                            const key = column.id;
                                            if(key !== 'actions') {
                                                const value = row[column.id];

                                                if (Array.isArray(value)) {
                                                    return (
                                                        <TableCell key={key}>
                                                            {value.join(' - ')}
                                                        </TableCell>
                                                    );
                                                } else if (typeof value == 'object') {
                                                    return (
                                                        <TableCell key={key}>
                                                            <TableCellUsers 
                                                                index={key}
                                                                row={value}
                                                            />
                                                        </TableCell>
                                                    );
                                                } else {
                                                    return (
                                                        <TableCell key={key}>
                                                            {value}
                                                        </TableCell>
                                                    );
                                                }
                                            }
                                            else {
                                                return (
                                                    <TableCell key={key}>
                                                        <Actions
                                                            id={row.id}
                                                            value={row}
                                                            status={row.status}
                                                        />
                                                    </TableCell>
                                                );
                                            }                                        
                                        })}
                                    </TableRow>
                                );
                            })}
                        </TableBody>
                    </Table>
                </TableContainer>
                <TablePagination
                    rowsPerPageOptions={[10, 25, 100]}
                    component="div"
                    count={rows.length}
                    rowsPerPage={rowsPerPage}
                    page={page}
                    onPageChange={handleChangePage}
                    onRowsPerPageChange={handleChangeRowsPerPage}
                />
            </Box>
        </Paper>
    );
}