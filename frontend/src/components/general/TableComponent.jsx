import { useState, useReducer } from 'react';

import { 
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TablePagination,
    TableRow,
    TableCellUsers,
    Toolbar,
    Typography,
    Box,
    Checkbox,
    Tooltip,
    IconButton
} from '@mui/material';

import DeleteIcon from '@mui/icons-material/Delete';
import FilterListIcon from '@mui/icons-material/FilterList';

import { global } from '@/styles/global';

function reducer (state, action) {
    const { type, value } = action;

    switch (type) {
        case 'change-row-per-page': {
            console.log(value)
            const newValue = state.rowsPerPage + value;

            return {
                ...state,
                rowsPerPage: newValue,
            }
        }
    }
}

const EnhancedTableToolbar = (props) => {
    const { title, numSelected, handleCheckbox, checkbox } = props;
  
    return (
        <Toolbar
            sx={{
                pl: { sm: 2 },
                pr: { xs: 1, sm: 1 },
                ...(numSelected > 0 && {
                    bgcolor: (theme) =>
                    alpha(theme.palette.primary.main, theme.palette.action.activatedOpacity),
                }),
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

            {checkbox ? (
                <Tooltip title="Delete">
                    <IconButton>
                        <DeleteIcon />
                    </IconButton>
                </Tooltip>
                ) : (
                <Tooltip title="Multiselección">
                    <IconButton onClick={() => handleCheckbox()}>
                        <FilterListIcon />
                    </IconButton>
                </Tooltip>
            )}
        </Toolbar>
    );
  }

export default function TableComponent (props) {
    const { title, columns, rows, checkbox, minWidth, Actions } = props;

    const [page, setPage] = useState(0);
    const [checkboxAction, setCheckboxAction] = useState(checkbox ? true : false);
    const [data, dispatch] = useReducer(reducer, {
        selected: [],
        order: 'asc',
        orderBy: columns[0].id,
        rowsPerPage: 10,
        rowCount: rows.length,
        numSelected: 0
    })

    const handleChangePage = (event, newPage) => {
        setPage(newPage);
    };

    const handleChangeRowsPerPage = (event) => {
        dispatch({type: 'change-row-per-page', value: event.target.value})
        setPage(0);
    };

    const handleCheckbox = () => {
        setCheckboxAction(true)
    }

    const handleSelectAll = (event) => {
        console.log(event)
    }

    return (
        <Paper sx={{...global.border, width: '100%', overflow: 'hidden'}}>
            <Box sx={{margin: 2}}>
                <EnhancedTableToolbar 
                    title={title}
                    handleCheckbox={handleCheckbox}
                    numSelected={data.selected.length}
                />
                <TableContainer sx={{ maxHeight: 500 }}>
                    <Table stickyHeader aria-label="sticky table" >
                        <TableHead>
                            <TableRow>
                                {checkboxAction &&
                                    <TableCell 
                                        padding="checkbox">
                                        <Checkbox
                                            color="primary"
                                            indeterminate={data.numSelected > 0 && data.selected.length < data.rowCount}
                                            checked={data.rowCount > 0 && data.numSelected === data.rowCount}
                                            onChange={handleSelectAll}
                                            inputProps={{
                                                'aria-label': 'select all desserts',
                                            }}
                                        /> 
                                    </TableCell>
                                }
                                {columns.map((column) => (
                                    <TableCell
                                        key={column.id}
                                        style={{ minWidth: minWidth }}
                                        sx={{textAlign: column.id == 'actions' ? 'center' : 'left' }}
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
                            {rows.slice(page * data.rowsPerPage, page * data.rowsPerPage + data.rowsPerPage)
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
                                        {checkboxAction &&
                                            <TableCell 
                                                padding="checkbox"
                                            >
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
                                                    <TableCell 
                                                        key={key}
                                                        sx={{textAlign: 'center'}}
                                                    >
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
                    rowsPerPage={data.rowsPerPage}
                    page={page}
                    onPageChange={handleChangePage}
                    onRowsPerPageChange={handleChangeRowsPerPage}
                    labelRowsPerPage='Filas por pagina'
                />
            </Box>
        </Paper>
    );
}